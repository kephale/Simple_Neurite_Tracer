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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.legacy.LegacyService;

import org.scijava.command.Command;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.widget.ChoiceWidget;

import ij.ImagePlus;
import ij.WindowManager;

/**
 * Implements the 'Choose Tracing Image (From Open Image)...' command.
 *
 * @author Tiago Ferreira
 */
@Plugin(initializer = "init", type = Command.class, visible = false,
	label = "Change Tracing Image")
public class ChooseDatasetCmd extends CommonDynamicCmd implements Command {

	@Parameter
	private LegacyService legacyService;

	@Parameter(label = "New tracing image:", persist = false, required = false,
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE)
	private String choice;

	@Parameter(label = "Validate spatial calibration", required = false,
		description = "Checks whether voxel dimensions of chosen image differ from those of loaded image (if any)")
	private boolean validateCalibration;

	private HashMap<String, ImagePlus> impMap;

	@Override
	public void run() {
		if (impMap == null || choice == null || impMap.isEmpty()) {
			error("No other open images seem to be available.");
		}
		else {
			final ImagePlus chosenImp = impMap.get(choice);
			if (compatibleCalibration(chosenImp)) snt.initialize(chosenImp);
		}
	}

	private boolean compatibleCalibration(final ImagePlus chosenImp) {
		if (!validateCalibration || !snt.accessToValidImageData())
			return true;
		if (!snt.getImagePlus().getCalibration().equals(chosenImp.getCalibration())) {
			final Result result = uiService.showDialog(
					"Images do not share the same spatial calibration.\n"
					+ "Load " + chosenImp.getTitle() + " nevertheless?",
					MessageType.QUESTION_MESSAGE);
			return (Result.YES_OPTION == result || Result.OK_OPTION == result);
		}
		return true;
	}

	@SuppressWarnings("unused")
	private void init() {
		if (!sntService.isActive()) {
			error("SNT is not running.");
			return;
		}
		snt = sntService.getPlugin();
		final MutableModuleItem<String> mItem = getInfo().getMutableInput("choice",
			String.class);
		final Collection<ImagePlus> impCollection = getImpInstances();
		if (impCollection == null || impCollection.isEmpty()) {
			resolveInputs();
			return;
		}
		impMap = new HashMap<>(impCollection.size());
		final ImagePlus existingImp = snt.getImagePlus();
		for (final ImagePlus imp : impCollection) {
			if (imp.equals(existingImp)) continue;
			impMap.put(imp.getTitle(), imp);
		}
		if (impMap.isEmpty()) {
			resolveInputs();
			return;
		}
		final List<String> choices = new ArrayList<>(impMap.keySet());
		Collections.sort(choices);
		mItem.setChoices(choices);
		if (choices.size() > 10) mItem.setWidgetStyle(ChoiceWidget.LIST_BOX_STYLE);
	}

	private void resolveInputs() {
		choice = null;
		resolveInput("choice");
		resolveInput("validateCalibration");
	}

	private Collection<ImagePlus> getImpInstances() {
		// In theory we should be able to use legacyService to retrieve
		// all the images but somehow this can never retrieve the full
		// list of current available instances:
//		return legacyService.getImageMap().getImagePlusInstances();
		final String[] titles = WindowManager.getImageTitles();
		final Collection<ImagePlus> imps = new ArrayList<>();
		for (final String title : titles) {
			// ignore side panes
			if (title.startsWith("ZY [") || title.startsWith("XZ [")) continue;
			imps.add(WindowManager.getImage(title));
		}
		return imps;
	}

	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ChooseDatasetCmd.class, true);
	}

}
