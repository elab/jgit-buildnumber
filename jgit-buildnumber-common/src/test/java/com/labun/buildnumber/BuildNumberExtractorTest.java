package com.labun.buildnumber;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class BuildNumberExtractorTest {

    @Test
    void test() {
        Parameters params = new SimpleParameters();
        params.setVerbose(true);
        params.setRepositoryDirectory(new File("."));
        params.setBuildNumberFormat("branch + '.' + commitsCount + '/' + commitDate + '/' + shortRevision + (dirty.length > 0 ? '-' + dirty : '')");

        assertDoesNotThrow(() -> {
            BuildNumberExtractor extractor = new BuildNumberExtractor(params, msg -> log.info(msg));
            extractor.extract();
        });
    }

}
