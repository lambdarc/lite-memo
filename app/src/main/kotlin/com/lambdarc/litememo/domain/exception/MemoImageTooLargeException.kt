package com.lambdarc.litememo.domain.exception

class MemoImageTooLargeException(maxBytes: Long) :
    IllegalArgumentException("Image exceeds the attachable size of $maxBytes bytes.")
