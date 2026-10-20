package com.calcsuite.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the picture that is sent to the provider.
 *
 * The type of a picture is read from the file itself, because the providers
 * look at the content and a file name can say anything.
 */
class AiImageTest {

    /** The first bytes of a real JPEG. */
    private val jpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
    )

    /** The first bytes of a real PNG. */
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** The first bytes of a real WebP. */
    private val webp = byteArrayOf(
        0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50,
    )

    @Test fun aJpegIsRecognised() {
        assertEquals(AiImage.JPEG, AiImage.mediaTypeOf(jpeg))
    }

    @Test fun aPngIsRecognised() {
        assertEquals(AiImage.PNG, AiImage.mediaTypeOf(png))
    }

    @Test fun aWebpIsRecognised() {
        assertEquals(AiImage.WEBP, AiImage.mediaTypeOf(webp))
    }

    @Test fun somethingElseIsNotRecognised() {
        assertNull(AiImage.mediaTypeOf(byteArrayOf(1, 2, 3, 4)))
        assertNull(AiImage.mediaTypeOf(ByteArray(0)))
    }

    @Test fun aTextFileIsNotRecognised() {
        assertNull(AiImage.mediaTypeOf("<html>not a picture</html>".toByteArray()))
    }

    @Test fun theBytesAreSentAsBase64() {
        // The three bytes 1, 2 and 3 are "AQID" in base64.
        val image = AiImage(byteArrayOf(1, 2, 3), AiImage.JPEG)
        assertEquals("AQID", image.base64())
    }

    @Test fun theDataUrlCarriesTheType() {
        val image = AiImage(byteArrayOf(1, 2, 3), AiImage.PNG)
        assertEquals("data:image/png;base64,AQID", image.dataUrl())
    }

    @Test fun twoPicturesWithTheSameBytesAreTheSame() {
        val first = AiImage(byteArrayOf(1, 2, 3), AiImage.JPEG)
        val second = AiImage(byteArrayOf(1, 2, 3), AiImage.JPEG)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test fun aPictureOfAnotherTypeIsNotTheSame() {
        val first = AiImage(byteArrayOf(1, 2, 3), AiImage.JPEG)
        val second = AiImage(byteArrayOf(1, 2, 3), AiImage.PNG)
        assert(first != second)
    }
}
