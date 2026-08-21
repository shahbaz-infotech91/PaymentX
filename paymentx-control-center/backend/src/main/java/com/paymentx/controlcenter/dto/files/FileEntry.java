package com.paymentx.controlcenter.dto.files;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real file inside the one explicitly-configured
 * directory this backend is allowed to browse (see
 * ControlCenterProperties.Files) - its real name, real size on disk,
 * and real last-modified time, read directly via java.nio.file. Never
 * a full filesystem path (only the bare filename, which is all the
 * download endpoint needs, and which reveals nothing about the host's
 * directory layout).
 *
 * HINGLISH: Is backend ke us ek explicitly-configured directory ke
 * andar ki ek real file (dekho ControlCenterProperties.Files) - iska
 * real naam, disk par real size, aur real last-modified time,
 * java.nio.file se directly padha gaya. Kabhi ek pura filesystem path
 * nahi (sirf bare filename, jo download endpoint ko chahiye hota hai,
 * aur jo host ke directory layout ke baare me kuch reveal nahi karta).
 */
public record FileEntry(
        String name,
        long sizeBytes,
        OffsetDateTime lastModified
) {
}
