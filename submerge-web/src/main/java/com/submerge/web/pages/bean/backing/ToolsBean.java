package com.submerge.web.pages.bean.backing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.primefaces.model.UploadedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.submerge.sub.convert.SubtitleConverter;
import com.submerge.sub.object.ass.ASSSub;
import com.submerge.sub.object.config.SubInput;
import com.submerge.sub.object.itf.TimedTextFile;
import com.submerge.sub.object.srt.SRTSub;
import com.submerge.sub.parser.ParserFactory;
import com.submerge.sub.utils.FileUtils;
import com.submerge.web.model.entity.SubtitleProfile;
import com.submerge.web.pages.bean.AbstractManagedBean;
import com.submerge.web.pages.bean.model.UserBean;
import com.submerge.web.pages.bean.model.UserSubConfigBean;
import com.submerge.web.service.UserService;
import com.submerge.web.utils.ProfileUtils;
import com.submerge.web.utils.UploadedFileUtils;

@Component("toolsBean")
@Scope(value = "session")
public class ToolsBean extends AbstractManagedBean implements Serializable {

	private static final long serialVersionUID = -4565775891674696585L;

	private static final transient Logger logger = Logger.getLogger(ToolsBean.class.getName());

	@Autowired
	private transient UserService userService;

	private UploadedFile uploadedFile;

	@Autowired
	private UserSubConfigBean userConfig;

	@Autowired
	private UserBean userBean;

	private String previewLanguage;

	// ======================== Public methods ==========================

	/**
	 * Parse a subtitle and convert it to ASS
	 * 
	 */
	public void convertAss() {

		try {
			File file = UploadedFileUtils.toFile(this.uploadedFile);
			String extension = FilenameUtils.getExtension(this.uploadedFile.getFileName());

			TimedTextFile ttf = ParserFactory.getParser(extension).parse(file);
			SubtitleProfile profile = this.userConfig.getProfileSimple();

			SubInput subInput = ProfileUtils.createSubInput(ttf, profile, "Default");

			SubtitleConverter convert = new SubtitleConverter();
			ASSSub ass = convert.toASS(subInput);

			String filename = FilenameUtils.getName(file.getName());
			String destFileName = StringUtils.removeEnd(filename, extension) + ".ass";

			writeString(destFileName, ass.toString());

			saveState();

			logger.log(Level.FINE, "File : " + file.getName() + " converted to ASS");
		} catch (Exception e) {
			FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
			FacesContext.getCurrentInstance().addMessage(null, msg);

			if (this.uploadedFile != null) {
				logger.log(Level.SEVERE,
						"Cannot convert " + this.uploadedFile.getFileName() + " to ass : " + e.getMessage());
			}
		} finally {
			this.uploadedFile = null;
		}
	}

	/**
	 * Parse a subtitle and convert it to SRT
	 * 
	 */
	public void convertSrt() {

		try {
			File file = UploadedFileUtils.toFile(this.uploadedFile);
			String extension = FilenameUtils.getExtension(this.uploadedFile.getFileName());

			TimedTextFile ttf = ParserFactory.getParser(extension).parse(file);
			SRTSub srtSub = new SubtitleConverter().toSRT(ttf);

			String filename = FilenameUtils.getName(file.getName());
			String destFileName = StringUtils.removeEnd(filename, extension) + ".srt";

			writeString(destFileName, srtSub.toString());

			saveState();

			logger.log(Level.FINE, "File : " + file.getName() + " converted to SRT");
		} catch (Exception e) {
			FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
			FacesContext.getCurrentInstance().addMessage(null, msg);

			if (this.uploadedFile != null) {
				logger.log(Level.SEVERE,
						"Cannot convert " + this.uploadedFile.getFileName() + " to srt : " + e.getMessage());
			}

		} finally {
			this.uploadedFile = null;
		}
	}

	/**
	 * Change the encoding charset of a file to UTF-8
	 * 
	 */
	public void convertUtf8() {

		try {
			File file = UploadedFileUtils.toFile(this.uploadedFile);
			String encoding = FileUtils.guessEncoding(file);

			StringBuffer sbf = new StringBuffer();
			StringBuilder sb = new StringBuilder(sbf);

			try (FileInputStream fis = new FileInputStream(file);
					InputStreamReader isr = new InputStreamReader(fis, encoding);
					BufferedReader br = new BufferedReader(isr)) {

				for (int c; (c = br.read()) != -1;) {
					sb.append((char) c);
				}
			}

			writeString(file.getName(), sb.toString());

			saveState();

			logger.log(Level.FINE, "File encoding changed from " + encoding + " - file : " + file.getName());
		} catch (Exception e) {
			FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
			FacesContext.getCurrentInstance().addMessage(null, msg);

			if (this.uploadedFile != null) {
				logger.log(Level.SEVERE,
						"Cannot convert " + this.uploadedFile.getFileName() + " to UTF-8 : " + e.getMessage());
			}
		} finally {
			this.uploadedFile = null;
		}

	}

	/**
	 * Update the language of the bottom preview
	 */
	public void updatePreviewLanguage() {

		String language = getRequestParameterMap().get("languageToSet");
		this.userConfig.getProfileSimple().setLanguage(language);
	}

	/**
	 * Method called each time the index page is loaded
	 */
	public void onPageLoad() {

		SubtitleProfile profile = this.userConfig.getProfileSimple();
		ProfileUtils.initProfiles(profile);

		this.previewLanguage = this.userConfig.getProfileSimple().getLanguage();

		if (StringUtils.isEmpty(this.previewLanguage)) {
			this.previewLanguage = this.userBean.getLanguage();
			this.userConfig.getProfileSimple().setLanguage(this.previewLanguage);
		}
	}

	// ======================== Private methods ==========================

	/**
	 * Write a string as a response
	 * 
	 * @param filename the filename
	 * @param message the string to write
	 * @throws IOException
	 * @throws UnsupportedEncodingException
	 */
	private void writeString(String filename, String message) throws IOException, UnsupportedEncodingException {

		FacesContext facesContext = FacesContext.getCurrentInstance();
		ExternalContext externalContext = getExternalContext();

		externalContext.responseReset();
		externalContext.setResponseContentType(this.uploadedFile.getContentType());
		externalContext.setResponseHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		OutputStream output = externalContext.getResponseOutputStream();
		output.write(message.getBytes("UTF-8"));

		facesContext.responseComplete();
	}

	/**
	 * Save user profiles in database if the user is logged
	 */
	private void saveState() {

		if (this.userBean.isLogged()) {
			this.userService.save(this.userBean.getUser());
		}
	}

	// ======================== GETTER and SETTER methods ==========================

	public UploadedFile getFile() {

		return this.uploadedFile;
	}

	public void setFile(UploadedFile uploadedFile) {

		this.uploadedFile = uploadedFile;
	}

	public String getPreviewLanguage() {
		return this.previewLanguage;
	}

	public void setPreviewLanguage(String previewLanguage) {
		this.previewLanguage = previewLanguage;
	}

	public String getPreview() {

		Locale locale = new Locale(this.previewLanguage);
		return getBundleMessages(locale).getString("sub.preview");
	}
	
}
