/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package tracing.gui.cmds;

import io.scif.services.DatasetIOService;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import net.imagej.ImageJ;
import net.imagej.display.ImageDisplayService;
import net.imagej.legacy.LegacyService;

import org.scijava.ItemVisibility;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.CompositeConverter;
import tracing.PathAndFillManager;
import tracing.SNT;
import tracing.SimpleNeuriteTracer;
import tracing.gui.GuiUtils;

/**
 * Command for Launching SNT
 *
 * @author Tiago Ferreira
 */
@Plugin(type = DynamicCommand.class, visible = true,
	menuPath = "Plugins>NeuroAnatomy>SNT...",
	initializer = "initialize")
public class SNTLoaderCmd extends DynamicCommand {

	@Parameter
	private DatasetIOService datasetIOService;
	@Parameter
	private DisplayService displayService;
	@Parameter
	private ImageDisplayService imageDisplayService;
	@Parameter
	private LegacyService legacyService;
	@Parameter
	private ConvertService convertService;
	@Parameter
	private UIService uiService;

	private static final String IMAGE_NONE = "None. Use a display canvas";
	private static final String IMAGE_FILE = "Image from path specified below";
	private static final String UI_SIMPLE = "Memory saving: Only XY view";
	private static final String UI_DEFAULT = "Default: XY, ZY and XZ views";
	private static final String DEF_DESCRIPTION =
		"Ignored when a display canvas is used";

	@Parameter(required = true, label = "Image", //
			description = "The image to be traced", //
			callback = "imageChoiceChanged")
	private String imageChoice;

	@Parameter(required = false, label = "Image file",
			description = "<HTML>Image file, when <i>Image</i> choice is <i>"+ IMAGE_FILE +"</i>",
		style = FileWidget.OPEN_STYLE, callback = "imageFileChanged")
	private File imageFile;

	@Parameter(required = false, label = "<HTML>&nbsp;",
		visibility = ItemVisibility.MESSAGE)
	private String SPACER1;

	@Parameter(required = false, label = "Reconstruction file", //
			description="The reconstruction file to be loaded (.traces, .(e)swc or .json)",
		style = FileWidget.OPEN_STYLE, callback = "tracesFileChanged")
	private File tracesFile;

	@Parameter(required = false, label = "<HTML>&nbsp;",
		visibility = ItemVisibility.MESSAGE)
	private String SPACER2;

	@Parameter(required = false, label = "User interface", choices = { UI_DEFAULT,
		UI_SIMPLE }, description = DEF_DESCRIPTION)
	private String uiChoice;

	@Parameter(required = false, label = "Tracing channel",
		description = DEF_DESCRIPTION, min = "1", //
		max = ""+ ij.CompositeImage.MAX_CHANNELS +"", callback = "channelChanged")
	private int channel;

	private ImagePlus sourceImp;
	private File currentImageFile;

	@Override
	public void initialize() {
		GuiUtils.setSystemLookAndFeel();
		// TODO: load defaults from prefService?
		sourceImp = legacyService.getImageMap().lookupImagePlus(imageDisplayService
			.getActiveImageDisplay());
		final MutableModuleItem<String> imageChoiceInput = getInfo()
			.getMutableInput("imageChoice", String.class);
		if (sourceImp == null) {
			imageChoiceInput.setChoices(Arrays.asList(IMAGE_NONE,
					IMAGE_FILE));
		}
		else {
			imageChoiceInput.setChoices(Arrays.asList(sourceImp
				.getTitle(), IMAGE_NONE, IMAGE_FILE));
			imageChoiceInput.setValue(this, sourceImp.getTitle());
			adjustChannelInput();
		}
	}

	private void adjustChannelInput() {
		if (sourceImp != null && sourceImp.getTitle().equals(imageChoice)) {
			channel = Math.min(channel, sourceImp.getNChannels());
		}
	}

	private void loadActiveImage() {
		if (sourceImp == null) {
			uiService.showDialog("There are no images open.");
			return;
		}
		if (sourceImp.getOriginalFileInfo() != null) {
			final String dir = sourceImp.getOriginalFileInfo().directory;
			final String file = sourceImp.getOriginalFileInfo().fileName;
			imageFile = (dir == null || file == null) ? null : new File(dir + file);
		}
		adjustFileFields(false);
	}

	@SuppressWarnings("unused")
	private void imageChoiceChanged() {
		switch (imageChoice) {
			case IMAGE_NONE:
				clearImageFileChoice();
				channel = 1;
				uiChoice = UI_SIMPLE;
				return;
			case IMAGE_FILE:
				if (null == imageFile) imageFile = currentImageFile;
				adjustFileFields(false);
				return;
			default: // imageChoice is now the title of frontmost image
				loadActiveImage();
				if (sourceImp != null) {
					clearImageFileChoice();
					if (sourceImp.getNSlices() == 1) uiChoice = UI_SIMPLE;
					adjustChannelInput();
				}
				return;
		}
	}

	@SuppressWarnings("unused")
	private void channelChanged() {
		adjustChannelInput();
	}

	private void clearImageFileChoice() {
		currentImageFile = imageFile;
		final MutableModuleItem<File> imageFileInput = getInfo().getMutableInput(
			"imageFile", File.class);
		imageFileInput.setValue(this, null);
	}

	@SuppressWarnings("unused")
	private void imageFileChanged() {
		adjustFileFields(true);
	}

	private void adjustFileFields(final boolean adjustImageChoice) {
		if (imageFile == null || !imageFile.exists()) {
			imageChoice = IMAGE_NONE;
			return;
		}
		if (adjustImageChoice) imageChoice = IMAGE_FILE;
		for (final String ext : new String[] { "traces", "swc" , "json"}) {
			final File candidate = SNT.findClosestPair(imageFile, ext);
			if (candidate != null && candidate.exists()) {
				tracesFile = candidate;
				break;
			}
			adjustChannelInput();
		}
	}

	@SuppressWarnings("unused")
	private void tracesFileChanged() {
		if (IMAGE_FILE.equals(imageChoice) || tracesFile == null || !tracesFile
			.exists()) return;
		final File candidate = SNT.findClosestPair(tracesFile, "tif");
		if (candidate != null && candidate.exists()) {
			imageFile = candidate;
			adjustChannelInput();
		}
	}

	
	@SuppressWarnings("unused")
	private void resetPrefs() {
		// There have been lots of reports of bugs caused simplify by persisting
		// experimental preferences. We'll wipe everything until this version is
		// properly released
		final ResetPrefsCmd resetPrefs = new ResetPrefsCmd();
		resetPrefs.setContext(context());
		resetPrefs.clearAll();
		SNT.log("Prefs reset");
	}

	@Override
	public void run() {

	//	resetPrefs();
		final boolean noImg = IMAGE_NONE.equals(imageChoice) || (IMAGE_FILE.equals(
			imageChoice) && imageFile == null);

		if (noImg) {
			final PathAndFillManager pathAndFillManager = new PathAndFillManager();
			if (tracesFile != null && tracesFile.exists()) {
				pathAndFillManager.setHeadless(true);
				if (!pathAndFillManager.load(tracesFile.getAbsolutePath()))
				{
					cancel(String.format("%s is not a valid file", tracesFile
						.getAbsolutePath()));
				}
			}

			initPlugin(new SimpleNeuriteTracer(getContext(), pathAndFillManager));
			return;
		}

		else if (IMAGE_FILE.equals(imageChoice)) {
			try {

//				final Dataset ds = datasetIOService.open(imageFile.getAbsolutePath());
//				sourceImp = convertService.convert(ds, ImagePlus.class);
//				displayService.createDisplay(sourceImp.getTitle(), sourceImp);

				// FIXME: In some cases the code above seems to open images as virtual
				// stacks which causes all sort of problems later on. We'll fallback
				// to IJ1 until this issue is fixed
				sourceImp = IJ.openImage(imageFile.getAbsolutePath());
				sourceImp.show();
			}
			catch (final Exception exc) {
				cancel("Could not open image:\n" + imageFile.getAbsolutePath());
				return;
			}

		}
		else if (sourceImp == null) { // frontmost image does not exist
			cancel("An image is required but none was found.");
			return;
		}

		// Is spatial calibration set?
		if (!validateImageDimensions()) return;

		// If user loaded an existing image, it is possible it can be RGB
		if (ImagePlus.COLOR_RGB == sourceImp.getType()) {
			final boolean convert = new GuiUtils().getConfirmation(
				"RGB images are (intentionally) not supported. You can however convert " +
					sourceImp.getTitle() +
					" to a multichannel image. Would you like to do it now? (SNT will quit if you choose \"No\")",
				"Convert to Multichannel?");
			if (!convert) return;
			sourceImp.hide();
			sourceImp = CompositeConverter.makeComposite(sourceImp);
			sourceImp.show();
		}

		final SimpleNeuriteTracer sntInstance = new SimpleNeuriteTracer(
			getContext(), sourceImp);
		sntInstance.loadTracings(tracesFile);
		initPlugin(sntInstance);

	}

	private void initPlugin(final SimpleNeuriteTracer snt)
	{
		try {
			final boolean singlePane = IMAGE_NONE.equals(imageChoice) || uiChoice.equals(UI_SIMPLE);
			final int frame = (sourceImp == null) ? 1 : sourceImp.getFrame();
			snt.initialize(singlePane, channel, frame);
			snt.startUI();
		}
		catch (final OutOfMemoryError error) {
			final StringBuilder sb = new StringBuilder(
				"Out of Memory: There is not enough RAM to load SNT under current options.\n");
			sb.append("Please allocate more memory to IJ or ");
			if (uiChoice.equals(UI_SIMPLE)) {
				sb.append("choose a smaller ").append((sourceImp == null) ? "file."
					: "image.");
			}
			else {
				sb.append("select the \"").append(UI_SIMPLE).append("\" interface.");
			}
			cancel(sb.toString());
		}
	}

	// this exists only to address issue #25 and avoid the propagation
	// of swc files in pixel coordinates
	private boolean validateImageDimensions() {
		final int[] dims = sourceImp.getDimensions();
		if (dims[4] > 1 && dims[3] == 1 && new GuiUtils().getConfirmation(
			"It appears that image has " + dims[4] + " timepoints but only 1 Z-slice. " +
				"Swap Z,T Dimensions?", "Swap Z,T Dimensions?"))
		{
			sourceImp.setDimensions(dims[2], dims[4], dims[3]);
		}
		final Calibration cal = sourceImp.getCalibration();
		if (!cal.scaled() || cal.pixelDepth < cal.pixelHeight ||
			cal.pixelDepth < cal.pixelWidth)
		{
			return new GuiUtils().getConfirmation("Spatial calibration of " +
				sourceImp.getTitle() +
				" appears to be unset or inaccurate. Continue nevertheless?",
				"Inaccurate Spatial Calibration?");
		}
		return true;
	}

	/*
	 * IDE debug method
	 *
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Object img = ij.io().open("/home/tferr/code/OP_1/OP_1.tif");
		ij.ui().show("OP_1", img);
		SNT.setDebugMode(true);
		ij.command().run(SNTLoaderCmd.class, true);
	}

}
