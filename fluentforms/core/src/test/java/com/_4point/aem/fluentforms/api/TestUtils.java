package com._4point.aem.fluentforms.api;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestUtils {
	public final static Path SAMPLE_FORMS_DIR = Paths.get("src","test", "resources", "SampleForms");
	public final static Path SAMPLE_FORM = SAMPLE_FORMS_DIR.resolve("SampleForm.xdp");
}
