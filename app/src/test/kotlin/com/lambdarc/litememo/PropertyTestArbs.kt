package com.lambdarc.litememo

import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.hiragana
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.printableAscii
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.zoneId
import io.kotest.property.arbitrary.zoneOffset
import java.time.ZoneId
import java.time.ZoneOffset

private val emojiCodepoints: Arb<Codepoint> = Arb.int(0x1F300..0x1FAFF).map(::Codepoint)

private val separatorCodepoints: Arb<Codepoint> =
    Arb.of('\n'.code, '\r'.code, '\t'.code, ' '.code, '　'.code).map(::Codepoint)

private val textCodepoints: Arb<Codepoint> = Arb.choice(
    Codepoint.printableAscii(),
    Codepoint.hiragana(),
    Codepoint.egyptianHieroglyphs(),
    emojiCodepoints,
    separatorCodepoints
)

internal fun Arb.Companion.text(lengthRange: IntRange = 0..40): Arb<String> =
    Arb.string(lengthRange, textCodepoints)

internal fun Arb.Companion.nonBlankText(lengthRange: IntRange = 1..40): Arb<String> =
    Arb.text(lengthRange).filter { it.isNotBlank() }

internal fun Arb.Companion.calendarZoneId(): Arb<ZoneId> = Arb.choice(
    Arb.zoneId(),
    Arb.zoneOffset(),
    Arb.of(
        ZoneOffset.ofHours(14),
        ZoneOffset.ofHours(-12),
        ZoneId.of("Pacific/Kiritimati"),
        ZoneId.of("Pacific/Apia"),
        ZoneId.of("America/New_York")
    )
)
