package com.lambdarc.litememo.ui.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lambdarc.litememo.R

class AppLockAuthenticator(private val activity: FragmentActivity) {

    private var pendingCallback: ((AppLockAuthenticationUiResult) -> Unit)? = null

    private val credentialLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val authResult = if (result.resultCode == Activity.RESULT_OK) {
                AppLockAuthenticationUiResult.SUCCEEDED
            } else {
                AppLockAuthenticationUiResult.CANCELED
            }
            dispatchResult(authResult)
        }

    private val biometricManager: BiometricManager
        get() = BiometricManager.from(activity)

    private val keyguardManager: KeyguardManager
        get() = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    fun authenticate(callback: (AppLockAuthenticationUiResult) -> Unit) {
        if (pendingCallback != null) {
            callback(AppLockAuthenticationUiResult.UNAVAILABLE)
            return
        }
        pendingCallback = callback

        when {
            !canAuthenticate() ->
                dispatchResult(AppLockAuthenticationUiResult.NO_DEVICE_CREDENTIAL)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                authenticateWithBiometricPrompt(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )

            canAuthenticateWithBiometric(BiometricManager.Authenticators.BIOMETRIC_WEAK) ->
                authenticateWithLegacyBiometricPrompt()

            else -> authenticateWithDeviceCredential()
        }
    }

    private fun canAuthenticate(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
        return canAuthenticateWithBiometric(BiometricManager.Authenticators.BIOMETRIC_WEAK) ||
            keyguardManager.isDeviceSecure
    }

    private fun authenticateWithLegacyBiometricPrompt() {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_prompt_title))
            .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)

        if (keyguardManager.isDeviceSecure) {
            builder.setNegativeButtonText(
                activity.getString(R.string.app_lock_use_device_credential)
            )
        } else {
            builder.setNegativeButtonText(activity.getString(R.string.cancel_label))
        }

        createPrompt(launchDeviceCredentialOnNegative = keyguardManager.isDeviceSecure)
            .authenticate(builder.build())
    }

    private fun authenticateWithBiometricPrompt(authenticators: Int) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_prompt_title))
            .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        createPrompt(launchDeviceCredentialOnNegative = false).authenticate(promptInfo)
    }

    private fun createPrompt(launchDeviceCredentialOnNegative: Boolean): BiometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    dispatchResult(AppLockAuthenticationUiResult.SUCCEEDED)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        launchDeviceCredentialOnNegative
                    ) {
                        authenticateWithDeviceCredential()
                        return
                    }
                    dispatchResult(errorCode.toAuthenticationResult())
                }
            }
        )

    @Suppress("DEPRECATION")
    private fun authenticateWithDeviceCredential() {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.app_lock_prompt_title),
            activity.getString(R.string.app_lock_prompt_subtitle)
        )

        if (intent == null) {
            dispatchResult(AppLockAuthenticationUiResult.NO_DEVICE_CREDENTIAL)
            return
        }

        credentialLauncher.launch(intent)
    }

    private fun canAuthenticateWithBiometric(authenticators: Int): Boolean =
        biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun dispatchResult(result: AppLockAuthenticationUiResult) {
        val callback = pendingCallback ?: return
        pendingCallback = null
        callback(result)
    }

    private fun Int.toAuthenticationResult(): AppLockAuthenticationUiResult = when (this) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AppLockAuthenticationUiResult.CANCELED

        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_NO_BIOMETRICS -> AppLockAuthenticationUiResult.NO_DEVICE_CREDENTIAL

        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> AppLockAuthenticationUiResult.UNAVAILABLE

        else -> AppLockAuthenticationUiResult.FAILED
    }

}
