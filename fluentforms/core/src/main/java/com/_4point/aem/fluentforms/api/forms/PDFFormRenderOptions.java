package com._4point.aem.fluentforms.api.forms;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com._4point.aem.fluentforms.api.AbsoluteOrRelativeUrl;
import com._4point.aem.fluentforms.api.Document;
import com._4point.aem.fluentforms.api.PathOrUrl;
import com.adobe.fd.forms.api.AcrobatVersion;
import com.adobe.fd.forms.api.CacheStrategy;

public interface PDFFormRenderOptions extends PDFFormRenderOptionsSetter {

	AcrobatVersion getAcrobatVersion();

	CacheStrategy getCacheStrategy();

	PathOrUrl getContentRoot();

	Path getDebugDir();

	Locale getLocale();

	List<AbsoluteOrRelativeUrl> getSubmitUrls();

	public Boolean getTaggedPDF();

	boolean isTaggedPDF();

	Document getXci();

}