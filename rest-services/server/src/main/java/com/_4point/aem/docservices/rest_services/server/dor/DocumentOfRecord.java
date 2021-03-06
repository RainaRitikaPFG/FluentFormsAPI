package com._4point.aem.docservices.rest_services.server.dor;

import static com._4point.aem.docservices.rest_services.server.FormParameters.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com._4point.aem.docservices.rest_services.server.ContentType;
import com._4point.aem.docservices.rest_services.server.Exceptions.BadRequestException;
import com._4point.aem.docservices.rest_services.server.Exceptions.InternalServerErrorException;
import com._4point.aem.docservices.rest_services.server.Exceptions.NotAcceptableException;
import com._4point.aem.docservices.rest_services.server.dor.DocumentOfRecordService.DocumentOfRecordException;
import com._4point.aem.docservices.rest_services.server.dor.DocumentOfRecordService.DocumentOfRecordOptions;
import com._4point.aem.docservices.rest_services.server.dor.DocumentOfRecordService.DocumentOfRecordOptionsBuilder;
import com._4point.aem.docservices.rest_services.server.dor.DocumentOfRecordService.DocumentOfRecordResult;
import com._4point.aem.docservices.rest_services.server.forms.ImportData;
import com.adobe.forms.common.service.FileAttachmentWrapper;

@SuppressWarnings("serial")
@Component(service=Servlet.class, property={Constants.SERVICE_DESCRIPTION + "=DocumentOfRecord.Generate Service",
											"sling.servlet.methods=" + HttpConstants.METHOD_POST})
@SlingServletPaths("/services/DorService/Generate")
public class DocumentOfRecord extends SlingAllMethodsServlet {

	private static final Logger log = LoggerFactory.getLogger(ImportData.class);

	private final Supplier<DocumentOfRecordService> dorServiceFactory = this::getAdobeDorService;

	@Reference
	private  com.adobe.aemds.guide.addon.dor.DoRService adobeDorService;	// Don't use this directly, use dorServiceFactory instead,
	
	@Override
	protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		try {
			this.processInput(request, response);
		} catch (BadRequestException br) {
			log.warn("Bad Request from the user", br);
			response.sendError(SlingHttpServletResponse.SC_BAD_REQUEST, br.getMessage());
		} catch (InternalServerErrorException ise) {
			log.error("Internal server error", ise);
			response.sendError(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR, ise.getMessage());
		} catch (NotAcceptableException nae) {
			log.error("NotAcceptable error", nae);
			response.sendError(SlingHttpServletResponse.SC_NOT_ACCEPTABLE, nae.getMessage());
		} catch (Exception e) {  			// Some exception we haven't anticipated.
			log.error(e.getMessage() != null ? e.getMessage() : e.getClass().getName() , e);	// Make sure this gets into our log.
			throw e;
		}
	}

	private void processInput(SlingHttpServletRequest request, SlingHttpServletResponse response) throws BadRequestException, InternalServerErrorException, NotAcceptableException {
		DorRenderFormParameters params = DorRenderFormParameters.readFormParameters(request);

		byte[] fileBytes = generateDoR(params);

		response.setContentType(ContentType.APPLICATION_PDF.toString());
		response.setContentLength(fileBytes.length);
		try {
			response.getOutputStream().write(fileBytes);
		} catch (IOException e) {
			throw new InternalServerErrorException("Error while writing response.", e);
		}
	}

	private byte[] generateDoR(DorRenderFormParameters params) throws InternalServerErrorException {
		DocumentOfRecordOptions dorOptions = params.toDorOptions();
		try {
			// Render doc of record...
			DocumentOfRecordResult dorResult = dorServiceFactory.get().render(dorOptions);

			// Get bytes....
			return dorResult.getContent();

		} catch (DocumentOfRecordException e) {
			throw new InternalServerErrorException("Error while rendering form '" + dorOptions.getFormResource().getPath() + "' caused by '" + e.getMessage() + "'.", e);
		}
	}
	
	/**
	 * Convert the xml to a String.  Parsing it through a DOM performs two things:
	 *   1) Validates the xml data sent from request against W3C standards.
	 *   2) Handles odd encodings by converting them to UTF-8
	 *    
	 * @param xml String value of xml data passed from request.
	 * @throws BadRequestException 
	 */
	private static String convertXmlDataToString(byte[] xml) throws BadRequestException
	{
		//---------------------------------------------------------------------
		// Valid the XML from the POST body
		//---------------------------------------------------------------------
		try {
			InputSource is = new InputSource(new ByteArrayInputStream(xml));
			Document parsedDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
			parsedDoc.setXmlStandalone(true);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(parsedDoc), new StreamResult(writer));
			return writer.toString();
		}
		catch (SAXException | IOException | ParserConfigurationException e) {
			String msg = "Input XML payload invalid: " + e.getMessage();
			throw new BadRequestException(msg, e);
		} catch (TransformerException e) {
			String msg = "Error converting XML payload to String: " + e.getMessage();
			throw new BadRequestException(msg, e);
		}
	}

	private DocumentOfRecordService getAdobeDorService() {
		return DocumentOfRecordService.DocumentOfRecordServiceImpl.of(this.adobeDorService);
	}
	
	private static class DorRenderFormParameters {
		private static final String TEMPLATE_PARAM = "template";
		private static final String DATA_PARAM = "data";
		private static final String LOCALE_PARAM = "locale";
		private static final String ATTACHMENT_PARAM = "attachment";
		private final String dataXml;
		private final String formURI;
		private final String locale;
		private final List<FileAttachmentWrapper> attachments;
		private final Resource formResource;
		
		private DorRenderFormParameters(String dataXml, String formURI, String locale, List<FileAttachmentWrapper> attachments, Resource formResource) {
			super();
			this.dataXml = dataXml;
			this.formURI = formURI;
			this.locale = locale;
			this.attachments = attachments;
			this.formResource = formResource;
		}

		public String getDataXml() {
			return this.dataXml;
		}

		public String getFormURI() {
			return this.formURI;
		}

		public String getLocale() {
			return this.locale;
		}

		public boolean hasAttachments() {
			return !this.attachments.isEmpty();
		}

		public List<FileAttachmentWrapper> getAttachments() {
			return this.attachments;
		}

		public Resource getFormResource() {
			return formResource;
		}

		public DocumentOfRecordOptions toDorOptions() {
			// Create a Document of Record 'Options' object.
			DocumentOfRecordOptionsBuilder dorOptionsBldr = DocumentOfRecordOptionsBuilder.create()
																	  .setData(this.dataXml)
																	  .setFormResource(this.formResource)
																	  .setLocale(new java.util.Locale(this.locale));
			
			if (this.hasAttachments()) {
				dorOptionsBldr.setIncludeAttachments(true)
							  .setFileAttachments(this.attachments);
			}
			
			 return dorOptionsBldr.build();
		}
		
		public static DorRenderFormParameters readFormParameters(SlingHttpServletRequest request) throws BadRequestException {
			String formURI = getMandatoryParameter(request, TEMPLATE_PARAM).getString();
			byte[] xmlData = getMandatoryParameter(request, DATA_PARAM).get();
			String xmlDataStr = convertXmlDataToString(xmlData);
			
			String locale = getOptionalParameter(request, LOCALE_PARAM).map(RequestParameter::getString).orElse("en");
			List<FileAttachmentWrapper> attachments = getOptionalParameters(request, ATTACHMENT_PARAM)
												.map(Arrays::asList)				// Convert Optional<RequestParameter[]> to Optional<List<RequestParameter>> 
												.orElse(Collections.emptyList())	// Convert to List<RequestParameter>
												.stream()
													.map(DorRenderFormParameters::fromRequestParameter)		// Convert List<RequestParameter> to List<byte[]>
													.collect(Collectors.toList());
			Resource formResource = request.getResourceResolver().getResource(formURI);

			return new DorRenderFormParameters(xmlDataStr, formURI, locale, attachments, formResource);
		}
		
		private static FileAttachmentWrapper fromRequestParameter(RequestParameter param) {
			return new FileAttachmentWrapper(param.getFileName(), param.getContentType(), param.get());
		}
	}
}
