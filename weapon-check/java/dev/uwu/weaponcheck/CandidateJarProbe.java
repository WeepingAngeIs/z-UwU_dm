package dev.uwu.weaponcheck;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import net.neoforged.fml.ModList;

/** Proves each disposable client suite loaded the same frozen product JAR. */
public final class CandidateJarProbe {
    private CandidateJarProbe() {}

    public static void verify() {
        String expectedFile = System.getProperty("uwu.check.candidateJar");
        String expectedSha = System.getProperty("uwu.check.candidateSha256");
        if (expectedFile == null || expectedSha == null) throw new IllegalStateException("The weapon fixture requires a packaged JAR");
        require(expectedFile != null && expectedSha != null && expectedSha.matches("(?i)[0-9a-f]{64}"),
                "Packaged mode requires both the candidate path and its expected SHA-256");

        try {
            Path expected = Path.of(expectedFile).toRealPath();
            require(Files.isRegularFile(expected), "Candidate is not a packaged file: " + expected);
            var info = ModList.get().getModFileById("uwudimension");
            require(info != null, "NeoForge did not load uwudimension");
            Path actual = info.getFile().getFilePath().toRealPath();
            require(actual.equals(expected), "Loaded uwudimension file differs: expected=" + expected + ", actual=" + actual);

            // A second source-output registration would invalidate packaged provenance even
            // if another locator also happened to discover a correctly named candidate JAR.
            for (String folder : System.getProperty("fml.modFolders", "").split(Pattern.quote(File.pathSeparator))) {
                require(!folder.startsWith("uwudimension%%"), "uwudimension is registered as a local source-output mod: " + folder);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(actual)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
            }
            String actualSha = HexFormat.of().formatHex(digest.digest());
            require(actualSha.equalsIgnoreCase(expectedSha),
                    "Loaded candidate SHA-256 differs: expected=" + expectedSha + ", actual=" + actualSha);
            System.out.println("UWU WEAPONS PACKAGED CANDIDATE PASS role=" + System.getProperty("uwu.check.role") + " sha256=" + actualSha + " path=" + actual);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("UWU PACKAGED CANDIDATE FAILED: could not verify the loaded JAR", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("UWU PACKAGED CANDIDATE FAILED: " + message);
    }
}
