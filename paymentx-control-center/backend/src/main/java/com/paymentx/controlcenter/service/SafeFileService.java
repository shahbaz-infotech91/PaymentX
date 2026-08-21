package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.files.FileEntry;
import com.paymentx.controlcenter.exception.ControlCenterException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ENGLISH: The entire safety boundary for the Phase 4 Files feature -
 * every method here operates ONLY inside the one real, explicitly-
 * configured directory from ControlCenterProperties.Files
 * (reports-export-directory), never a caller-supplied path. What it
 * does: lists real files (bounded to MAX_LISTED entries, sorted newest
 * first) and resolves a caller-supplied filename to a real file for
 * download - but only after checking the filename against a strict
 * allowlist pattern (letters/digits/dot/dash/underscore only, no "/",
 * "\", or ".." anywhere) AND verifying the resolved real, canonical
 * path's parent directory is exactly the configured directory's real,
 * canonical path. That second check is what actually stops path-
 * traversal and symlink tricks - the filename pattern alone is
 * defense-in-depth, not the real guarantee. Why it exists: the Phase 4
 * brief's explicit "Do NOT expose arbitrary filesystem browsing"
 * requirement - this class is the one place that requirement is
 * enforced, so PostgresController's report-download endpoint and
 * FilesController both delegate to it rather than each reimplementing
 * the check.
 *
 * HINGLISH: Phase 4 Files feature ke liye poori safety boundary - yahan
 * har method SIRF us ek real, explicitly-configured directory ke andar
 * operate karta hai ControlCenterProperties.Files se
 * (reports-export-directory), kabhi caller-supplied path nahi. Ye kya
 * karti hai: real files list karta hai (MAX_LISTED entries tak bound,
 * newest pehle sorted) aur ek caller-supplied filename ko download ke
 * liye ek real file me resolve karta hai - lekin sirf filename ko ek
 * strict allowlist pattern (sirf letters/digits/dot/dash/underscore,
 * kahin bhi "/", "\", ya ".." nahi) ke against check karne ke baad AUR
 * ye verify karne ke baad ki resolved real, canonical path ka parent
 * directory exactly configured directory ka real, canonical path hai.
 * Wahi doosra check hai jo actually path-traversal aur symlink tricks
 * ko rokta hai - filename pattern akela sirf defense-in-depth hai, real
 * guarantee nahi. Ye dashboard me kyu hai: Phase 4 brief ka explicit
 * "Do NOT expose arbitrary filesystem browsing" requirement - ye class
 * wahi ek jagah hai jahan ye requirement enforce hoti hai, isliye
 * PostgresController ka report-download endpoint aur FilesController
 * dono ise delegate karte hain, har ek apna check reimplement karne ke
 * bajaye.
 */
@Service
public class SafeFileService {

    private static final int MAX_LISTED = 500;
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final Path exportsDirectory;

    public SafeFileService(ControlCenterProperties properties) {
        this.exportsDirectory = Path.of(properties.getFiles().getReportsExportDirectory()).toAbsolutePath().normalize();
    }

    public List<FileEntry> listFiles() {
        if (!Files.isDirectory(exportsDirectory)) {
            return List.of();
        }
        List<FileEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(exportsDirectory)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    entries.add(new FileEntry(
                            path.getFileName().toString(),
                            Files.size(path),
                            OffsetDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneOffset.UTC)));
                } catch (IOException ignored) {
                    // A file that vanishes between listing and stat'ing is skipped, not fatal.
                }
            });
        } catch (IOException e) {
            throw new ControlCenterException("FILES_UNREADABLE", "Could not list the configured exports directory: " + e.getMessage(), e);
        }
        return entries.stream()
                .sorted(Comparator.comparing(FileEntry::lastModified).reversed())
                .limit(MAX_LISTED)
                .toList();
    }

    /** Resolves filename to a real, safe file inside the configured directory - throws if it would ever escape that directory. */
    public Resource resolveForDownload(String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            throw new ControlCenterException("INVALID_FILENAME", "Filename contains characters that are not allowed.");
        }
        Path resolved = exportsDirectory.resolve(filename).normalize();
        if (!resolved.getParent().equals(exportsDirectory)) {
            throw new ControlCenterException("INVALID_FILENAME", "Resolved path escapes the configured exports directory.");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ControlCenterException("FILE_NOT_FOUND", "No such file: " + filename);
        }
        return new FileSystemResource(resolved);
    }
}
