package io.github.azagniotov.stubby4j.filesystem;

import io.github.azagniotov.stubby4j.cli.ANSITerminal;
import io.github.azagniotov.stubby4j.stubs.StubRepository;
import io.github.azagniotov.stubby4j.utils.FileUtils;
import io.github.azagniotov.stubby4j.yaml.YAMLParser;

import java.io.File;
import java.util.Date;
import java.util.Map;

public final class ExternalFilesScanner implements Runnable {

    private final long sleepTime;
    private final StubRepository stubRepository;

    public ExternalFilesScanner(final StubRepository stubRepository, final long sleepTime) {
        this.sleepTime = sleepTime;
        this.stubRepository = stubRepository;
        ANSITerminal.status(String.format("External file scan enabled, watching external files referenced from %s", stubRepository.getYAMLConfigCanonicalPath()));
    }

    @Override
    public void run() {

        try {
            final Map<File, Long> externalFiles = stubRepository.getExternalFiles();

            while (!Thread.currentThread().isInterrupted()) {

                Thread.sleep(sleepTime);

                boolean isContinue = true;
                String offendingFilename = "";
                for (Map.Entry<File, Long> entry : externalFiles.entrySet()) {
                    final File file = entry.getKey();
                    final long lastModified = entry.getValue();
                    final long currentFileModified = file.lastModified();

                    if (lastModified < currentFileModified) {
                        externalFiles.put(file, currentFileModified);
                        isContinue = false;
                        offendingFilename = file.getAbsolutePath();
                        break;
                    }
                }

                if (isContinue) {
                    continue;
                }

                ANSITerminal.info(String.format("%sExternal file scan detected change in %s%s", FileUtils.BR, offendingFilename, FileUtils.BR));

                try {
                    stubRepository.refreshStubsFromYAMLConfig(new YAMLParser());
                    ANSITerminal.ok(String.format("%sSuccessfully performed live refresh of main YAML with external files from: %s on [" + new Date().toString().trim() + "]%s",
                            FileUtils.BR,
                            stubRepository.getYAMLConfig(),
                            FileUtils.BR));
                } catch (final Exception ex) {
                    ANSITerminal.error("Could not refresh YAML configuration: " + ex.toString());
                    ANSITerminal.warn(String.format("YAML refresh aborted, previously loaded stubs remain untouched"));
                }
            }

        } catch (final Exception ex) {
            ex.printStackTrace();
            ANSITerminal.error("Could not perform live YAML scan: " + ex.toString());
        }
    }
}