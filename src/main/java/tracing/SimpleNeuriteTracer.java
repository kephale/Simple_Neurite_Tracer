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

package tracing;

import io.scif.services.DatasetIOService;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imagej.legacy.LegacyService;

import org.scijava.Context;
import org.scijava.NullContextException;
import org.scijava.app.StatusService;
import org.scijava.command.CommandService;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.util.ColorRGB;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Point3f;

import amira.AmiraMeshDecoder;
import amira.AmiraParameters;
import features.ComputeCurvatures;
import features.GaussianGenerationCallback;
import features.TubenessProcessor;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.ImageRoi;
import ij.gui.NewImage;
import ij.gui.Overlay;
import ij.gui.StackWindow;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij3d.Content;
import ij3d.ContentConstants;
import ij3d.ContentCreator;
import ij3d.Image3DUniverse;
import tracing.event.SNTEvent;
import tracing.event.SNTListener;
import tracing.gui.GuiUtils;
import tracing.gui.SWCImportOptionsDialog;
import tracing.gui.SigmaPalette;
import tracing.gui.SwingSafeResult;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.plugin.ShollTracingsCmd;
import tracing.util.BoundingBox;
import tracing.util.PointInCanvas;
import tracing.util.PointInImage;

/* Note on terminology:

   "traces" files are made up of "paths".  Paths are non-branching
   sequences of adjacent points (including diagonals) in the image.
   Branches and joins are supported by attributes of paths that
   specify that they begin on (or end on) other paths.

*/

public class SimpleNeuriteTracer extends MultiDThreePanes implements
	SearchProgressCallback, GaussianGenerationCallback, PathAndFillListener
{

	@Parameter
	private Context context;
	@Parameter
	protected StatusService statusService;
	@Parameter
	protected LegacyService legacyService;
	@Parameter
	protected LogService logService;
	@Parameter
	protected DatasetIOService datasetIOService;
	@Parameter
	protected ConvertService convertService;

	protected static boolean verbose = false; // FIXME: Use prefservice

	protected static final int MIN_SNAP_CURSOR_WINDOW_XY = 2;
	protected static final int MIN_SNAP_CURSOR_WINDOW_Z = 0;
	protected static final int MAX_SNAP_CURSOR_WINDOW_XY = 40;
	protected static final int MAX_SNAP_CURSOR_WINDOW_Z = 10;

	protected static final String startBallName = "Start point";
	protected static final String targetBallName = "Target point";
	protected static final int ballRadiusMultiplier = 5;

	protected PathAndFillManager pathAndFillManager;
	protected SNTPrefs prefs;
	private GuiUtils guiUtils;

	/* Legacy 3D Viewer. This is all deprecated stuff */
	protected Image3DUniverse univ;
	protected boolean use3DViewer;
	private Content imageContent;
	protected ImagePlus colorImage;
	protected static final int DISPLAY_PATHS_SURFACE = 1;
	protected static final int DISPLAY_PATHS_LINES = 2;
	protected static final int DISPLAY_PATHS_LINES_AND_DISCS = 3;

	/* UI preferences */
	protected boolean useCompressedXML = true;
	volatile protected int cursorSnapWindowXY;
	volatile protected int cursorSnapWindowZ;
	volatile protected boolean autoCanvasActivation;
	volatile protected boolean panMode;
	volatile protected boolean snapCursor;
	volatile protected boolean unsavedPaths = false;
	protected volatile boolean showOnlySelectedPaths;
	protected volatile boolean showOnlyActiveCTposPaths;

	/*
	 * Just for convenience, keep casted references to the superclass's
	 * InteractiveTracerCanvas objects:
	 */
	private InteractiveTracerCanvas xy_tracer_canvas;
	private InteractiveTracerCanvas xz_tracer_canvas;
	private InteractiveTracerCanvas zy_tracer_canvas;

	/* Image properties */
	protected int width, height, depth;
	protected int imageType = -1;
	protected double x_spacing = 1;
	protected double y_spacing = 1;
	protected double z_spacing = 1;
	protected String spacing_units = SNT.getSanitizedUnit(null);
	protected int channel;
	protected int frame;
	private LUT lut;

	/* loaded pixels (main image) */
	protected byte[][] slices_data_b;
	protected short[][] slices_data_s;
	protected float[][] slices_data_f;
	volatile protected float stackMax = Float.MIN_VALUE;
	volatile protected float stackMin = Float.MAX_VALUE;

	/* Curvatures */
	private volatile boolean hessianEnabled = false;
	private ComputeCurvatures hessian = null;
	/*
	 * This variable just stores the sigma which the current 'hessian'
	 * ComputeCurvatures was / is being calculated (or -1 if 'hessian' is null) ...
	 */
	private volatile double hessianSigma = -1;

	/* tracing threads */
	private TracerThread currentSearchThread = null;
	private ManualTracerThread manualSearchThread = null;

	/*
	 * Fields for tracing on secondary data: a filtered image. This can work in one
	 * of two ways: image is loaded into memory or we waive its file path to a
	 * third-party class that will parse it
	 */
	protected boolean doSearchOnFilteredData;
	protected float[][] filteredData;
	protected File filteredFileImage = null;
	protected boolean tubularGeodesicsTracingEnabled = false;
	protected TubularGeodesicsTracer tubularGeodesicsThread;

	/*
	 * pathUnfinished indicates that we have started to create a path, but not yet
	 * finished it (in the sense of moving on to a new path with a differen starting
	 * point.) FIXME: this may be redundant - check that.
	 */
	volatile boolean pathUnfinished = false;
	private Path editingPath; // Path being edited when in 'Edit Mode'

	/* Labels */
	protected String[] materialList;
	byte[][] labelData;

	volatile boolean loading = false;
	volatile boolean lastStartPointSet = false;

	double last_start_point_x;
	double last_start_point_y;
	double last_start_point_z;

	Path endJoin;
	PointInImage endJoinPoint;

	/*
	 * If we've finished searching for a path, but the user hasn't confirmed that
	 * they want to keep it yet, temporaryPath is non-null and holds the Path we
	 * just searched out.
	 */

	// Any method that deals with these two fields should be synchronized.
	Path temporaryPath = null;
	Path currentPath = null;

	/* GUI */
	protected SNTUI ui;
	protected volatile boolean tracingHalted = false; // Tracing functions paused?

	// This should only be assigned to when synchronized on this object
	// (FIXME: check that that is true)
	FillerThread filler = null;

	/* Colors */
	private static final Color DEFAULT_SELECTED_COLOR = Color.GREEN;
	protected static final Color DEFAULT_DESELECTED_COLOR = Color.MAGENTA;
	protected static final Color3f DEFAULT_SELECTED_COLOR3F = new Color3f(
		Color.GREEN);
	protected static final Color3f DEFAULT_DESELECTED_COLOR3F = new Color3f(
		Color.MAGENTA);
	protected Color3f selectedColor3f = DEFAULT_SELECTED_COLOR3F;
	protected Color3f deselectedColor3f = DEFAULT_DESELECTED_COLOR3F;

	// TODO: Refactor: Adopt getters and setters
	public Color selectedColor = DEFAULT_SELECTED_COLOR;
	public Color deselectedColor = DEFAULT_DESELECTED_COLOR;
	public boolean displayCustomPathColors = true;

	@Deprecated
	protected SimpleNeuriteTracer() {
		final Context context = (Context) IJ.runPlugIn("org.scijava.Context", "");
		context.inject(this);
		SNT.setPlugin(this);
		pathAndFillManager = new PathAndFillManager(this);
		tracingHalted = true;
		enableAstar(false);
	}

	/**
	 * Instantiates SimpleNeuriteTracer in 'Tracing Mode'.
	 *
	 * @param context the SciJava application context providing the services
	 *          required by the class
	 * @param sourceImage the source image
	 * @throws IllegalArgumentException If sourceImage is of type 'RGB'
	 */
	public SimpleNeuriteTracer(final Context context, final ImagePlus sourceImage)
		throws IllegalArgumentException
	{

		if (context == null) throw new NullContextException();
		if (sourceImage.getStackSize() == 0) throw new IllegalArgumentException(
			"Uninitialized image object");
		if (sourceImage.getType() == ImagePlus.COLOR_RGB)
			throw new IllegalArgumentException(
				"RGB images are not supported. Please convert to multichannel and re-run");

		context.inject(this);
		SNT.setPlugin(this);
		setFieldsFromImage(sourceImage);
		pathAndFillManager = new PathAndFillManager(this);
		prefs = new SNTPrefs(this);
		prefs.loadPluginPrefs();
	}

	/**
	 * Instantiates SimpleNeuriteTracer in 'Analysis Mode'
	 *
	 * @param context the SciJava application context providing the services
	 *          required by the class
	 * @param pathAndFillManager The PathAndFillManager instance to be associated
	 *          with the plugin
	 */
	public SimpleNeuriteTracer(final Context context,
		final PathAndFillManager pathAndFillManager)
	{

		if (context == null) throw new NullContextException();
		if (pathAndFillManager == null) throw new IllegalArgumentException(
			"pathAndFillManager cannot be null");
		this.pathAndFillManager = pathAndFillManager;

		context.inject(this);
		SNT.setPlugin(this);
		prefs = new SNTPrefs(this);
		pathAndFillManager.plugin = this;
		pathAndFillManager.addPathAndFillListener(this);
		pathAndFillManager.setHeadless(true);

		// Inherit spacing from PathAndFillManager{
		final BoundingBox box = pathAndFillManager.getBoundingBox(false);
		x_spacing = box.xSpacing;
		y_spacing = box.ySpacing;
		z_spacing = box.zSpacing;
		spacing_units = box.getUnit();

		// now load preferences and disable auto-tracing features
		prefs.loadPluginPrefs();
		tracingHalted = true;
		enableAstar(false);
		enableSnapCursor(false);
		pathAndFillManager.setHeadless(false);
	}

	private void setFieldsFromImage(final ImagePlus sourceImage) {
		xy = sourceImage;
		width = sourceImage.getWidth();
		height = sourceImage.getHeight();
		depth = sourceImage.getNSlices();
		imageType = sourceImage.getType();
		singleSlice = depth == 1;
		setSinglePane(single_pane);
		final Calibration calibration = sourceImage.getCalibration();
		if (calibration != null) {
			x_spacing = calibration.pixelWidth;
			y_spacing = calibration.pixelHeight;
			z_spacing = calibration.pixelDepth;
			spacing_units = SNT.getSanitizedUnit(calibration.getUnit());
		}
		if ((x_spacing == 0.0) || (y_spacing == 0.0) || (z_spacing == 0.0)) {
			throw new IllegalArgumentException(
				"One dimension of the calibration information was zero: (" + x_spacing +
					"," + y_spacing + "," + z_spacing + ")");
		}

	}

	/**
	 * Rebuilds display canvases, i.e., the placeholder canvases used when no
	 * valid image data exists (a single-canvas is rebuilt if only the XY view is
	 * active).
	 * <p>
	 * Useful when multiple files are imported and imported paths 'fall off' the
	 * dimensions of current canvas(es). If there is not enough memory to
	 * accommodate enlarged dimensions, the resulting canvas will be a 2D image.
	 * </p>
	 *
	 * @throws IllegalArgumentException if valid image data exists
	 */
	public void rebuildDisplayCanvases() throws IllegalArgumentException {
		if (accessToValidImageData()) throw new IllegalArgumentException(
			"Attempting to rebuild canvas(es) when valid data exists");
		initialize(getSinglePane(), 1, 1);
		updateUIFromInitializedImp(xy.isVisible());
		pauseTracing(true, false);
		updateAllViewers();
	}

	private void updateUIFromInitializedImp(final boolean showImp) {
		if (getUI() != null) getUI().inputImageChanged();
		if (showImp) {
			xy.show();
			if (zy != null) zy.show();
			if (xz != null) xz.show();
		}
	}

	private void nullifyCanvases() {
		if (xy != null) {
			xy.changes = false;
			xy.close();
			xy = null;
		}
		if (zy != null) {
			zy.changes = false;
			zy.close();
			zy = null;
		}
		if (xz != null) {
			xz.changes = false;
			xz.close();
			xz = null;
		}
		xy_canvas = null;
		xz_canvas = null;
		zy_canvas = null;
		xy_window = null;
		xz_window = null;
		zy_window = null;
		xy_tracer_canvas = null;
		xz_tracer_canvas = null;
		zy_tracer_canvas = null;
		slices_data_b = null;
		slices_data_s = null;
		slices_data_f = null;
		hessian = null;
	}

	protected boolean accessToValidImageData() {
		return getImagePlus() != null && !"SNT Display Canvas".equals(xy
			.getInfoProperty());
	}

	private void setIsDisplayCanvas(final ImagePlus imp) {
		imp.setProperty("Info", "SNT Display Canvas");
	}

	private void assembleDisplayCanvases() {
		nullifyCanvases();
		if (pathAndFillManager.size() == 0) {
			// not enough information to proceed. Assemble a dummy canvas instead
			xy = NewImage.createByteImage("Display Canvas", 1, 1, 1,
				NewImage.FILL_BLACK);
			setFieldsFromImage(xy);
			setIsDisplayCanvas(xy);
			return;
		}
		BoundingBox box = pathAndFillManager.getBoundingBox(false);
		if (Double.isNaN(box.getDiagonal())) box = pathAndFillManager
			.getBoundingBox(true);

		final double[] dims = box.getDimensions(false);
		width = (int) Math.round(dims[0]);
		height = (int) Math.round(dims[1]);
		depth = (int) Math.round(dims[2]);
		spacing_units = box.getUnit();
		singleSlice = depth == 1;
		setSinglePane(single_pane);

		// Make canvas 2D if there is not enough memory (>80%) for a 3D stack
		// TODO: Remove ij.IJ dependency
		final double MEM_FRACTION = 0.8d;
		final long memNeeded = (long) width * height * depth; // 1 byte per pixel
		final long memMax = IJ.maxMemory(); // - 100*1024*1024;
		final long memInUse = IJ.currentMemory();
		final long memAvailable = (long) (MEM_FRACTION * (memMax - memInUse));
		if (memMax > 0 && memNeeded > memAvailable) {
			singleSlice = true;
			depth = 1;
			SNT.log(
				"Not enough memory for displaying 3D stack. Defaulting to 2D canvas");
		}

		// Enlarge canvas for easier access to edge nodes. Center all paths in
		// canvas
		// without translating their coordinates. This is more relevant for files
		// with
		// negative coordinates
		final int XY_PADDING = 50;
		final int Z_PADDING = (singleSlice) ? 0 : 2;
		width += XY_PADDING;
		height += XY_PADDING;
		depth += Z_PADDING;
		final PointInImage unscaledOrigin = box.unscaledOrigin();
		final PointInCanvas canvasOffset = new PointInCanvas(-unscaledOrigin.x +
			XY_PADDING / 2, -unscaledOrigin.y + XY_PADDING / 2, -unscaledOrigin.z +
				Z_PADDING / 2);
		for (final Path p : pathAndFillManager.getPaths()) {
			p.setCanvasOffset(canvasOffset);
		}

		// Create image
		imageType = ImagePlus.GRAY8;
		xy = NewImage.createByteImage("Display Canvas", width, height, depth,
			NewImage.FILL_BLACK);
		setIsDisplayCanvas(xy);
		xy.setCalibration(box.getCalibration());
		x_spacing = box.xSpacing;
		y_spacing = box.ySpacing;
		z_spacing = box.zSpacing;
		spacing_units = box.getUnit();
	}

	@Override
	public void initialize(final ImagePlus imp) {
		nullifyCanvases();
		xy = imp;
		setFieldsFromImage(imp);
		changeUIState(SNTUI.LOADING);
		initialize(getSinglePane(), channel = imp.getC(), frame = imp.getT());
		tracingHalted = !inputImageLoaded();
		updateUIFromInitializedImp(imp.isVisible());
	}

	/**
	 * Initializes the plugin by assembling all the required tracing views
	 *
	 * @param singlePane if true only the XY view will be generated, if false XY,
	 *          ZY, XZ views are created
	 * @param channel the channel to be traced. Ignored when no valid image data
	 *          exists.
	 * @param frame the frame to be traced. Ignored when no valid image data
	 *          exists.
	 */
	public void initialize(final boolean singlePane, final int channel,
		final int frame)
	{
		if (!accessToValidImageData()) {
			this.channel = 1;
			this.frame = 1;
			assembleDisplayCanvases();
		}
		else {
			this.channel = channel;
			this.frame = frame;
		}

		setSinglePane(singlePane);
		final Overlay sourceImageOverlay = xy.getOverlay();
		initialize(xy, frame);
		xy.setOverlay(sourceImageOverlay);

		xy_tracer_canvas = (InteractiveTracerCanvas) xy_canvas;
		xz_tracer_canvas = (InteractiveTracerCanvas) xz_canvas;
		zy_tracer_canvas = (InteractiveTracerCanvas) zy_canvas;
		addListener(xy_tracer_canvas);

		if (accessToValidImageData()) loadData();

		if (!single_pane) {
			final double min = xy.getDisplayRangeMin();
			final double max = xy.getDisplayRangeMax();
			xz.setDisplayRange(min, max);
			zy.setDisplayRange(min, max);
			addListener(xz_tracer_canvas);
			addListener(zy_tracer_canvas);
		}

	}

	private void addListener(final InteractiveTracerCanvas canvas) {
		final QueueJumpingKeyListener listener = new QueueJumpingKeyListener(this,
			canvas);
		setAsFirstKeyListener(canvas, listener);
	}

	public void reloadImage(final int channel, final int frame) {
		if (getImagePlus() == null || getImagePlus().getProcessor() == null)
			throw new IllegalArgumentException("No image has yet been loaded.");
		if (frame < 1 || channel < 1 || frame > getImagePlus().getNFrames() ||
			channel > getImagePlus().getNChannels())
			throw new IllegalArgumentException("Invalid position: C=" + channel +
				" T=" + frame);
		this.channel = channel;
		this.frame = frame;
		loadData();
		if (!single_pane) reloadZYXZpanes(frame);
		hessian = null;
		if (use3DViewer && imageContent != null) {
			updateImageContent(prefs.get3DViewerResamplingFactor());
		}
	}

	public void rebuildZYXZpanes() {
		single_pane = false;
		reloadZYXZpanes(frame);
		xy_tracer_canvas = (InteractiveTracerCanvas) xy_canvas;
		addListener(xy_tracer_canvas);
		zy_tracer_canvas = (InteractiveTracerCanvas) zy_canvas;
		addListener(zy_tracer_canvas);
		xz_tracer_canvas = (InteractiveTracerCanvas) xz_canvas;
		addListener(xz_tracer_canvas);
		if (!xy.isVisible()) xy.show();
		if (!zy.isVisible()) zy.show();
		if (!xz.isVisible()) xz.show();
	}

	private void loadData() {
		final ImageStack s = xy.getStack();
		switch (imageType) {
			case ImagePlus.GRAY8:
			case ImagePlus.COLOR_256:
				slices_data_b = new byte[depth][];
				for (int z = 0; z < depth; ++z)
					slices_data_b[z] = (byte[]) s.getPixels(xy.getStackIndex(channel, z +
						1, frame));
				stackMin = 0;
				stackMax = 255;
				break;
			case ImagePlus.GRAY16:
				slices_data_s = new short[depth][];
				for (int z = 0; z < depth; ++z)
					slices_data_s[z] = (short[]) s.getPixels(xy.getStackIndex(channel, z +
						1, frame));
				statusService.showStatus("Finding stack minimum / maximum");
				for (int z = 0; z < depth; ++z) {
					for (int y = 0; y < height; ++y)
						for (int x = 0; x < width; ++x) {
							final short v = slices_data_s[z][y * width + x];
							if (v < stackMin) stackMin = v;
							if (v > stackMax) stackMax = v;
						}
					statusService.showProgress(z, depth);
				}
				statusService.showProgress(0, 0);
				break;
			case ImagePlus.GRAY32:
				slices_data_f = new float[depth][];
				for (int z = 0; z < depth; ++z)
					slices_data_f[z] = (float[]) s.getPixels(xy.getStackIndex(channel, z +
						1, frame));
				statusService.showStatus("Finding stack minimum / maximum");
				for (int z = 0; z < depth; ++z) {
					for (int y = 0; y < height; ++y)
						for (int x = 0; x < width; ++x) {
							final float v = slices_data_f[z][y * width + x];
							if (v < stackMin) stackMin = v;
							if (v > stackMax) stackMax = v;
						}
					statusService.showProgress(z, depth);
				}
				statusService.showProgress(0, 0);
				break;
		}
		hessian = null; // ensure it will be reloaded
		updateLut();
	}

	public void startUI() {
		final SimpleNeuriteTracer thisPlugin = this;
		ui = SwingSafeResult.getResult(() -> new SNTUI(thisPlugin));
		guiUtils = new GuiUtils(ui);
		ui.displayOnStarting();
	}

	public void loadTracings(final File file) {
		if (file != null && file.exists()) {
			if (isUIready()) ui.changeState(SNTUI.LOADING);
			pathAndFillManager.load(file.getAbsolutePath());
			if (isUIready()) ui.resetState();
		}
	}

	public boolean pathsUnsaved() {
		return unsavedPaths;
	}

	public PathAndFillManager getPathAndFillManager() {
		return pathAndFillManager;
	}

	protected InteractiveTracerCanvas getXYCanvas() {
		return xy_tracer_canvas;
	}

	protected InteractiveTracerCanvas getXZCanvas() {
		return xz_tracer_canvas;
	}

	protected InteractiveTracerCanvas getZYCanvas() {
		return zy_tracer_canvas;
	}

	public ImagePlus getImagePlus() {
		//return (isDummy()) ? xy : getImagePlus(XY_PLANE);
		return getImagePlus(XY_PLANE);
	}

	protected double getImpDiagonalLength(final boolean scaled,
		final boolean xyOnly)
	{
		final BoundingBox box = getPathAndFillManager().getBoundingBox(false);
		final double[] dims = box.getDimensions(scaled);
		final PointInImage pim1 = new PointInImage(0, 0, 0);
		final PointInImage pim2 = new PointInImage(dims[0], dims[1], (xyOnly) ? 0
			: dims[2]);
		return pim1.distanceTo(pim2);
	}

	/* This overrides the method in ThreePanes... */
	@Override
	public InteractiveTracerCanvas createCanvas(final ImagePlus imagePlus,
		final int plane)
	{
		return new InteractiveTracerCanvas(imagePlus, this, plane,
			pathAndFillManager);
	}

	public void cancelSearch(final boolean cancelFillToo) {
		if (currentSearchThread != null) currentSearchThread.requestStop();
		if (manualSearchThread != null) manualSearchThread.requestStop();
		if (tubularGeodesicsThread != null) tubularGeodesicsThread.requestStop();
		endJoin = null;
		endJoinPoint = null;
		if (cancelFillToo && filler != null) filler.requestStop();
	}

	@Override
	public void threadStatus(final SearchInterface source, final int status) {
		// Ignore this information.
	}

	public void changeUIState(final int newState) {
		if (ui != null) ui.changeState(newState);
	}

	public int getUIState() {
		return (ui == null) ? -1 : ui.getCurrentState();
	}

	synchronized public void saveFill() {

		if (filler != null) {
			// The filler must be paused while we save to
			// avoid concurrent modifications...

			SNT.log("[" + Thread.currentThread() +
				"] going to lock filler in plugin.saveFill");
			synchronized (filler) {
				SNT.log("[" + Thread.currentThread() + "] acquired it");
				if (SearchThread.PAUSED == filler.getThreadStatus()) {
					// Then we can go ahead and save:
					pathAndFillManager.addFill(filler.getFill());
					// ... and then stop filling:
					filler.requestStop();
					ui.changeState(SNTUI.WAITING_TO_START_PATH);
					filler = null;
				}
				else {
					guiUtils.error("The filler must be paused before saving the fill.");
				}

			}
			SNT.log("[" + Thread.currentThread() + "] left lock on filler");
		}
	}

	synchronized public void discardFill() {
		discardFill(true);
	}

	synchronized public void discardFill(final boolean updateState) {
		if (filler != null) {
			synchronized (filler) {
				filler.requestStop();
				if (updateState) ui.resetState();
				filler = null;
			}
		}
	}

	synchronized public void pauseOrRestartFilling() {
		if (filler != null) {
			filler.pauseOrUnpause();
		}
	}

	/* Listeners */
	protected List<SNTListener> listeners = Collections.synchronizedList(
		new ArrayList<SNTListener>());

	public void addListener(final SNTListener listener) {
		listeners.add(listener);
	}

	public void notifyListeners(final SNTEvent event) {
		for (final SNTListener listener : listeners.toArray(new SNTListener[0])) {
			listener.onEvent(event);
		}
	}

	public boolean anyListeners() {
		return listeners.size() > 0;
	}

	/*
	 * Now a couple of callback methods, which get information about the progress of
	 * the search.
	 */

	@Override
	public void finished(final SearchInterface source, final boolean success) {

		/*
		 * This is called by both filler and currentSearchThread, so distinguish these
		 * cases:
		 */

		if (source == currentSearchThread || source == tubularGeodesicsThread ||
			source == manualSearchThread)
		{

			removeSphere(targetBallName);

			if (success) {
				final Path result = source.getResult();
				if (result == null) {
					SNT.error("Bug! Succeeded, but null result.");
					return;
				}
				if (endJoin != null) {
					result.setEndJoin(endJoin, endJoinPoint);
				}
				setTemporaryPath(result);

				if (ui.confirmTemporarySegments) {
					changeUIState(SNTUI.QUERY_KEEP);
				}
				else {
					confirmTemporary();
					ui.changeState(SNTUI.PARTIAL_PATH);
				}
			}
			else {

				ui.changeState(SNTUI.PARTIAL_PATH);
			}

			// Indicate in the dialog that we've finished...

			if (source == currentSearchThread) {
				currentSearchThread = null;
			}

		}

		removeThreadToDraw(source);
		updateAllViewers();

	}

	@Override
	public void pointsInSearch(final SearchInterface source, final int inOpen,
		final int inClosed)
	{
		// Just use this signal to repaint the canvas, in case there's
		// been no mouse movement.
		updateAllViewers();
	}

	public void justDisplayNearSlices(final boolean value, final int eitherSide) {

		xy_tracer_canvas.just_near_slices = value;
		if (!single_pane) {
			xz_tracer_canvas.just_near_slices = value;
			zy_tracer_canvas.just_near_slices = value;
		}

		xy_tracer_canvas.eitherSide = eitherSide;
		if (!single_pane) {
			xz_tracer_canvas.eitherSide = eitherSide;
			zy_tracer_canvas.eitherSide = eitherSide;
		}

		updateAllViewers();

	}

	protected boolean uiReadyForModeChange() {
		return isUIready() && (getUIState() == SNTUI.WAITING_TO_START_PATH ||
			getUIState() == SNTUI.TRACING_PAUSED);
	}

	// if (uiReadyForModeChange(SNTUI.ANALYSIS_MODE)) {
	// getGuiUtils().tempMsg("Tracing image not available");
	// return;
	// }
	protected Path getEditingPath() {
		return editingPath;
	}

	protected int getEditingNode() {
		return (getEditingPath() == null) ? -1 : getEditingPath()
			.getEditableNodeIndex();
	}

	/**
	 * Assesses if activation of 'Edit Mode' is possible.
	 *
	 * @return true, if possible, false otherwise
	 */
	public boolean editModeAllowed() {
		return editModeAllowed(false);
	}

	protected boolean editModeAllowed(final boolean warnUserIfNot) {
		final boolean uiReady = uiReadyForModeChange() || isEditModeEnabled();
		if (warnUserIfNot && !uiReady) {
			discreteMsg("Please finish current operation before editing paths");
			return false;
		}
		detectEditingPath();
		final boolean pathExists = editingPath != null;
		if (warnUserIfNot && !pathExists) {
			discreteMsg("You must select a single path in order to edit it");
			return false;
		}
		final boolean validPath = pathExists && !editingPath.getUseFitted();
		if (warnUserIfNot && !validPath) {
			discreteMsg(
				"Only unfitted paths can be edited.<br>Run \"Un-fit volume\" to proceed");
			return false;
		}
		return uiReady && pathExists && validPath;
	}

	protected void setEditingPath(final Path path) {
		editingPath = path;
	}

	protected void detectEditingPath() {
		editingPath = getSingleSelectedPath();
	}

	protected Path getSingleSelectedPath() {
		final Collection<Path> sPaths = getSelectedPaths();
		if (sPaths == null || sPaths.size() != 1) return null;
		return getSelectedPaths().iterator().next();
	}

	protected void enableEditMode(final boolean enable) {
		if (enable) {
			changeUIState(SNTUI.EDITING);
			if (isUIready() && !getUI().nearbySlices()) getUI().togglePartsChoice();
		}
		else {
			if (ui != null) ui.resetState();
		}
		if (enable && pathAndFillManager.getSelectedPaths().size() == 1) {
			editingPath = getSelectedPaths().iterator().next();
		}
		else {
			if (editingPath != null) editingPath.setEditableNode(-1);
			editingPath = null;
		}
		setDrawCrosshairsAllPanes(!enable);
		setLockCursorAllPanes(enable);
		xy_tracer_canvas.setEditMode(enable);
		if (!single_pane) {
			xz_tracer_canvas.setEditMode(enable);
			zy_tracer_canvas.setEditMode(enable);
		}
		updateAllViewers();
	}

	protected void pause(final boolean pause) {
		if (pause) {
			if (!uiReadyForModeChange()) {
				guiUtils.error("Please finish/abort current task before pausing SNT.");
				return;
			}
			changeUIState(SNTUI.SNT_PAUSED);
			disableEventsAllPanes(true);
			setDrawCrosshairsAllPanes(false);
			setCanvasLabelAllPanes(InteractiveTracerCanvas.SNT_PAUSED_LABEL);
		}
		else {
			if (xy != null && xy.isLocked() && ui != null && !getConfirmation(
				"Image appears to be locked by other process. Activate SNT nevertheless?",
				"Image Locked")) return;
			disableEventsAllPanes(false);
			pauseTracing(tracingHalted, false);
		}
	}

	protected void pauseTracing(final boolean pause,
		final boolean validateChange)
	{
		if (pause) {
			if (validateChange && !uiReadyForModeChange()) {
				guiUtils.error(
					"Please finish/abort current task before pausing tracing.");
				return;
			}
			tracingHalted = true;
			changeUIState(SNTUI.TRACING_PAUSED);
			setDrawCrosshairsAllPanes(false);
			setCanvasLabelAllPanes(InteractiveTracerCanvas.TRACING_PAUSED_LABEL);
			enableSnapCursor(snapCursor && !accessToValidImageData());
		}
		else {
			tracingHalted = false;
			changeUIState(SNTUI.WAITING_TO_START_PATH);
			setDrawCrosshairsAllPanes(true);
			setCanvasLabelAllPanes(null);
		}
	}

	protected boolean isEditModeEnabled() {
		return isUIready() && SNTUI.EDITING == getUIState();
	}

	@Deprecated
	public void setCrosshair(final double new_x, final double new_y,
		final double new_z)
	{
		xy_tracer_canvas.setCrosshairs(new_x, new_y, new_z, true);
		if (!single_pane) {
			xz_tracer_canvas.setCrosshairs(new_x, new_y, new_z, true);
			zy_tracer_canvas.setCrosshairs(new_x, new_y, new_z, true);
		}
	}

	public void updateCursor(final double new_x, final double new_y,
		final double new_z)
	{
		xy_tracer_canvas.updateCursor(new_x, new_y, new_z);
		if (!single_pane) {
			xz_tracer_canvas.updateCursor(new_x, new_y, new_z);
			zy_tracer_canvas.updateCursor(new_x, new_y, new_z);
		}

	}

	synchronized public void loadLabelsFile(final String path) {

		final AmiraMeshDecoder d = new AmiraMeshDecoder();

		if (!d.open(path)) {
			guiUtils.error("Could not open the labels file '" + path + "'");
			return;
		}

		final ImageStack stack = d.getStack();

		final ImagePlus labels = new ImagePlus("Label file for Tracer", stack);

		if ((labels.getWidth() != width) || (labels.getHeight() != height) ||
			(labels.getNSlices() != depth))
		{
			guiUtils.error(
				"The size of that labels file doesn't match the size of the image you're tracing.");
			return;
		}

		// We need to get the AmiraParameters object for that image...

		final AmiraParameters parameters = d.parameters;

		materialList = parameters.getMaterialList();

		labelData = new byte[depth][];
		for (int z = 0; z < depth; ++z) {
			labelData[z] = (byte[]) stack.getPixels(xy.getStackIndex(channel, z + 1,
				frame));
		}

	}

	protected File loadedImageFile() {
		try {
			final FileInfo fInfo = getImagePlus().getFileInfo();
			return new File(fInfo.directory, fInfo.fileName);
		}
		catch (final NullPointerException npe) {
			return null;
		}
	}

	synchronized protected void loadTracesFile() {
		loading = true;
		final File suggestedFile = SNT.findClosestPair(prefs.getRecentFile(),
			".traces");
		final File chosenFile = guiUtils.openFile("Open .traces file...",
			suggestedFile, Collections.singletonList(".traces"));
		if (chosenFile == null) return; // user pressed cancel;

		if (!chosenFile.exists()) {
			guiUtils.error(chosenFile.getAbsolutePath() + " is no longer available");
			loading = false;
			return;
		}

		final int guessedType = PathAndFillManager.guessTracesFileType(chosenFile
			.getAbsolutePath());
		switch (guessedType) {
			case PathAndFillManager.TRACES_FILE_TYPE_COMPRESSED_XML:
				if (pathAndFillManager.loadCompressedXML(chosenFile.getAbsolutePath()))
					unsavedPaths = false;
				break;
			case PathAndFillManager.TRACES_FILE_TYPE_UNCOMPRESSED_XML:
				if (pathAndFillManager.loadUncompressedXML(chosenFile
					.getAbsolutePath())) unsavedPaths = false;
				break;
			default:
				guiUtils.error(chosenFile.getAbsolutePath() +
					" is not a valid traces file.");
				break;
		}

		loading = false;
	}

	synchronized protected void loadSWCFile() {
		loading = true;
		final File suggestedFile = SNT.findClosestPair(prefs.getRecentFile(),
			".swc");
		final File chosenFile = guiUtils.openFile("Open SWC file...", suggestedFile,
			Arrays.asList(".swc", ".eswc"));
		if (chosenFile == null) return; // user pressed cancel;

		if (!chosenFile.exists()) {
			guiUtils.error(chosenFile.getAbsolutePath() + " is no longer available");
			loading = false;
			return;
		}

		final int guessedType = PathAndFillManager.guessTracesFileType(chosenFile
			.getAbsolutePath());
		switch (guessedType) {
			case PathAndFillManager.TRACES_FILE_TYPE_SWC: {
				final SWCImportOptionsDialog swcImportDialog =
					new SWCImportOptionsDialog("SWC import options for " + chosenFile
						.getName());
				if (swcImportDialog.succeeded() && pathAndFillManager.importSWC(
					chosenFile.getAbsolutePath(), swcImportDialog.getIgnoreCalibration(),
					swcImportDialog.getXOffset(), swcImportDialog.getYOffset(),
					swcImportDialog.getZOffset(), swcImportDialog.getXScale(),
					swcImportDialog.getYScale(), swcImportDialog.getZScale(),
					swcImportDialog.getReplaceExistingPaths())) unsavedPaths = false;
				break;
			}
			default:
				guiUtils.error(chosenFile.getAbsolutePath() +
					" does not seem to contain valid SWC data.");
				break;
		}
		loading = false;
	}

	synchronized public void loadTracings() {

		loading = true;
		final File suggestedFile = SNT.findClosestPair(prefs.getRecentFile(),
			".traces");
		final File chosenFile = guiUtils.openFile("Open .traces or .(e)swc file...",
			suggestedFile, Arrays.asList(".traces", ".swc", ".eswc"));
		if (chosenFile == null) return; // user pressed cancel;

		if (!chosenFile.exists()) {
			guiUtils.error(chosenFile.getAbsolutePath() + " is no longer available");
			loading = false;
			return;
		}

		final int guessedType = PathAndFillManager.guessTracesFileType(chosenFile
			.getAbsolutePath());
		switch (guessedType) {
			case PathAndFillManager.TRACES_FILE_TYPE_SWC: {
				final SWCImportOptionsDialog swcImportDialog =
					new SWCImportOptionsDialog("SWC import options for " + chosenFile
						.getName());
				if (swcImportDialog.succeeded() && pathAndFillManager.importSWC(
					chosenFile.getAbsolutePath(), swcImportDialog.getIgnoreCalibration(),
					swcImportDialog.getXOffset(), swcImportDialog.getYOffset(),
					swcImportDialog.getZOffset(), swcImportDialog.getXScale(),
					swcImportDialog.getYScale(), swcImportDialog.getZScale(),
					swcImportDialog.getReplaceExistingPaths())) unsavedPaths = false;
				break;
			}
			case PathAndFillManager.TRACES_FILE_TYPE_COMPRESSED_XML:
				if (pathAndFillManager.loadCompressedXML(chosenFile.getAbsolutePath()))
					unsavedPaths = false;
				break;
			case PathAndFillManager.TRACES_FILE_TYPE_UNCOMPRESSED_XML:
				if (pathAndFillManager.loadUncompressedXML(chosenFile
					.getAbsolutePath())) unsavedPaths = false;
				break;
			default:
				guiUtils.error("The file '" + chosenFile.getAbsolutePath() +
					"' was of unknown type (" + guessedType + ")");
				break;
		}

		loading = false;
	}

	public void mouseMovedTo(final double x_in_pane, final double y_in_pane,
		final int in_plane, final boolean shift_key_down,
		final boolean join_modifier_down)
	{

		double x, y, z;

		final double[] pd = new double[3];
		findPointInStackPrecise(x_in_pane, y_in_pane, in_plane, pd);
		x = pd[0];
		y = pd[1];
		z = pd[2];

		final boolean editing = isEditModeEnabled() && editingPath != null &&
			editingPath.isSelected();
		final boolean joining = !editing && join_modifier_down && pathAndFillManager
			.anySelected();

		PointInImage pim = null;
		if (joining) {
			// find the nearest node to this cursor position
			pim = pathAndFillManager.nearestJoinPointOnSelectedPaths(x, y, z);
		}
		else if (editing) {
			// find the nearest node to this cursor 2D position.
			// then activate the Z-slice of the retrieved node
			final int eNode = editingPath.indexNearestToCanvasPosition2D(x, y,
				xy_tracer_canvas.nodeDiameter());
			if (eNode != -1) {
				pim = editingPath.getNode(eNode);
				editingPath.setEditableNode(eNode);
			}
		}
		if (pim != null) {
			x = pim.x / x_spacing;
			y = pim.y / y_spacing;
			z = pim.z / z_spacing;
			setCursorTextAllPanes((joining) ? " Fork Point" : null);
		}
		else {
			setCursorTextAllPanes(null);
		}

		final int ix = (int) Math.round(x);
		final int iy = (int) Math.round(y);
		final int iz = (int) Math.round(z);

		if (shift_key_down || editing) setZPositionAllPanes(ix, iy, iz);

		String statusMessage = "";
		if (editing && editingPath.getEditableNodeIndex() > -1) {
			statusMessage = "Node " + editingPath.getEditableNodeIndex() + ", ";
//			System.out.println("unscaled "+ editingPath.getPointInCanvas(editingPath
//					.getEditableNodeIndex()));
//			System.out.println("scaled "+ editingPath.getPointInImage(editingPath
//					.getEditableNodeIndex()));
		}
		statusMessage += "World: (" + SNT.formatDouble(ix * x_spacing, 2) + ", " +
			SNT.formatDouble(iy * y_spacing, 2) + ", " + SNT.formatDouble(iz *
				z_spacing, 2) + ");";
		if (labelData != null) {
			final byte b = labelData[iz][iy * width + ix];
			final int m = b & 0xFF;
			final String material = materialList[m];
			statusMessage += ", " + material;
		}
		statusMessage += " | Image: (" + ix + ", " + iy + ", " + (iz + 1) + ")";
		updateCursor(x, y, z);
		statusService.showStatus(statusMessage);
		repaintAllPanes(); // Or the crosshair isn't updated...

		if (filler != null) {
			synchronized (filler) {
				final float distance = filler.getDistanceAtPoint(ix, iy, iz);
				ui.getFillManager().showMouseThreshold(distance);
			}
		}
	}

	// When we set temporaryPath, we also want to update the display:

	@SuppressWarnings("deprecation")
	synchronized public void setTemporaryPath(final Path path) {

		final Path oldTemporaryPath = this.temporaryPath;

		xy_tracer_canvas.setTemporaryPath(path);
		if (!single_pane) {
			zy_tracer_canvas.setTemporaryPath(path);
			xz_tracer_canvas.setTemporaryPath(path);
		}

		temporaryPath = path;

		if (temporaryPath != null) temporaryPath.setName("Temporary Path");
		if (use3DViewer) {

			if (oldTemporaryPath != null) {
				oldTemporaryPath.removeFrom3DViewer(univ);
			}
			if (temporaryPath != null) temporaryPath.addTo3DViewer(univ, getXYCanvas()
				.getTemporaryPathColor(), null);
		}
	}

	@SuppressWarnings("deprecation")
	synchronized public void setCurrentPath(final Path path) {
		final Path oldCurrentPath = this.currentPath;
		currentPath = path;
		if (currentPath != null) {
			if (pathAndFillManager.getPathFromID(currentPath.getID()) == null)
				currentPath.setName("Current Path");
			path.setSelected(true); // so it is rendered as an active path
		}
		xy_tracer_canvas.setCurrentPath(path);
		if (!single_pane) {
			zy_tracer_canvas.setCurrentPath(path);
			xz_tracer_canvas.setCurrentPath(path);
		}
		if (use3DViewer) {
			if (oldCurrentPath != null) {
				oldCurrentPath.removeFrom3DViewer(univ);
			}
			if (currentPath != null) currentPath.addTo3DViewer(univ, getXYCanvas()
				.getTemporaryPathColor(), null);
		}
	}

	synchronized public Path getCurrentPath() {
		return currentPath;
	}

	public void setPathUnfinished(final boolean unfinished) {

		this.pathUnfinished = unfinished;
		xy_tracer_canvas.setPathUnfinished(unfinished);
		if (!single_pane) {
			zy_tracer_canvas.setPathUnfinished(unfinished);
			xz_tracer_canvas.setPathUnfinished(unfinished);
		}
	}

	void addThreadToDraw(final SearchInterface s) {
		xy_tracer_canvas.addSearchThread(s);
		if (!single_pane) {
			zy_tracer_canvas.addSearchThread(s);
			xz_tracer_canvas.addSearchThread(s);
		}
	}

	void removeThreadToDraw(final SearchInterface s) {
		xy_tracer_canvas.removeSearchThread(s);
		if (!single_pane) {
			zy_tracer_canvas.removeSearchThread(s);
			xz_tracer_canvas.removeSearchThread(s);
		}
	}

	int[] selectedPaths = null;

	/*
	 * Create a new 8 bit ImagePlus of the same dimensions as this image, but with
	 * values set to either 255 (if there's a point on a path there) or 0
	 */

	synchronized public ImagePlus makePathVolume(final Collection<Path> paths) {

		final byte[][] snapshot_data = new byte[depth][];

		for (int i = 0; i < depth; ++i)
			snapshot_data[i] = new byte[width * height];

		pathAndFillManager.setPathPointsInVolume(paths, snapshot_data, width,
			height, depth);

		final ImageStack newStack = new ImageStack(width, height);

		for (int i = 0; i < depth; ++i) {
			final ByteProcessor thisSlice = new ByteProcessor(width, height);
			thisSlice.setPixels(snapshot_data[i]);
			newStack.addSlice(null, thisSlice);
		}

		final ImagePlus newImp = new ImagePlus(xy.getShortTitle() +
			" Rendered Paths", newStack);
		newImp.setCalibration(xy.getCalibration());
		return newImp;
	}

	synchronized public ImagePlus makePathVolume() {
		return makePathVolume(pathAndFillManager.getPaths());
	}

	/* Start a search thread looking for the goal in the arguments: */

	synchronized void testPathTo(final double world_x, final double world_y,
		final double world_z, final PointInImage joinPoint)
	{

		if (!lastStartPointSet) {
			statusService.showStatus(

				"No initial start point has been set.  Do that with a mouse click." +
					" (Or a Shift-" + GuiUtils.ctrlKey() +
					"-click if the start of the path should join another neurite.");
			return;
		}

		if (temporaryPath != null) {
			statusService.showStatus(
				"There's already a temporary path; Press 'N' to cancel it or 'Y' to keep it.");
			return;
		}

		double real_x_end, real_y_end, real_z_end;

		int x_end, y_end, z_end;
		if (joinPoint == null) {
			real_x_end = world_x;
			real_y_end = world_y;
			real_z_end = world_z;
		}
		else {
			real_x_end = joinPoint.x;
			real_y_end = joinPoint.y;
			real_z_end = joinPoint.z;
			endJoin = joinPoint.onPath;
			endJoinPoint = joinPoint;
		}

		addSphere(targetBallName, real_x_end, real_y_end, real_z_end, getXYCanvas()
			.getTemporaryPathColor(), x_spacing * ballRadiusMultiplier);

		x_end = (int) Math.round(real_x_end / x_spacing);
		y_end = (int) Math.round(real_y_end / y_spacing);
		z_end = (int) Math.round(real_z_end / z_spacing);

		if (tubularGeodesicsTracingEnabled) {

			// Then useful values are:
			// oofFile.getAbsolutePath() - the filename of the OOF file
			// last_start_point_[xyz] - image coordinates of the start point
			// [xyz]_end - image coordinates of the end point

			// [xyz]_spacing

			tubularGeodesicsThread = new TubularGeodesicsTracer(filteredFileImage,
				(int) Math.round(last_start_point_x), (int) Math.round(
					last_start_point_y), (int) Math.round(last_start_point_z), x_end,
				y_end, z_end, x_spacing, y_spacing, z_spacing, spacing_units);
			addThreadToDraw(tubularGeodesicsThread);
			tubularGeodesicsThread.addProgressListener(this);
			tubularGeodesicsThread.start();

		}

		else if (!isAstarEnabled()) {
			manualSearchThread = new ManualTracerThread(this, last_start_point_x,
				last_start_point_y, last_start_point_z, x_end, y_end, z_end);
			addThreadToDraw(manualSearchThread);
			manualSearchThread.addProgressListener(this);
			manualSearchThread.start();
		}
		else {
			currentSearchThread = new TracerThread(xy, stackMin, stackMax, 0, // timeout
				// in
				// seconds
				1000, // reportEveryMilliseconds
				(int) Math.round(last_start_point_x), (int) Math.round(
					last_start_point_y), (int) Math.round(last_start_point_z), x_end,
				y_end, z_end, //
				true, // reciprocal
				is2D(), (hessianEnabled ? hessian : null), ui.getMultiplier(),
				doSearchOnFilteredData ? filteredData : null, hessianEnabled);

			addThreadToDraw(currentSearchThread);
			currentSearchThread.setDrawingColors(Color.CYAN, null);// TODO: Make this
																															// color a
																															// preference
			currentSearchThread.setDrawingThreshold(-1);
			currentSearchThread.addProgressListener(this);
			currentSearchThread.start();

		}

		updateAllViewers();
	}

	synchronized public void confirmTemporary() {

		if (temporaryPath == null)
			// Just ignore the request to confirm a path (there isn't one):
			return;

		currentPath.add(temporaryPath);

		final PointInImage last = currentPath.lastPoint();
		last_start_point_x = (int) Math.round(last.x / x_spacing);
		last_start_point_y = (int) Math.round(last.y / y_spacing);
		last_start_point_z = (int) Math.round(last.z / z_spacing);

		if (currentPath.endJoins == null) {
			setTemporaryPath(null);
			changeUIState(SNTUI.PARTIAL_PATH);
			updateAllViewers();
		}
		else {
			setTemporaryPath(null);
			// Since joining onto another path for the end must finish the path:
			finishedPath();
		}

		/*
		 * This has the effect of removing the path from the 3D viewer and adding it
		 * again:
		 */
		setCurrentPath(currentPath);
	}

	synchronized public void cancelTemporary() {

		if (!lastStartPointSet) {
			discreteMsg(
				"No initial start point has been set yet.<br>Do that with a mouse click or a Shift+" +
					GuiUtils.ctrlKey() +
					"-click if the start of the path should join another.");
			return;
		}

		if (temporaryPath == null) {
			discreteMsg("There is no temporary path to discard");
			return;
		}

		removeSphere(targetBallName);

		if (temporaryPath.endJoins != null) {
			temporaryPath.unsetEndJoin();
		}

		setTemporaryPath(null);

		endJoin = null;
		endJoinPoint = null;

		updateAllViewers();
	}

	synchronized public void cancelPath() {

		// Is there an unconfirmed path? If so, warn people about it...
		if (temporaryPath != null) {
			discreteMsg(
				"You need to confirm the last segment before canceling the path.");
			return;
		}

		if (currentPath != null) {
			if (currentPath.startJoins != null) currentPath.unsetStartJoin();
			if (currentPath.endJoins != null) currentPath.unsetEndJoin();
		}

		removeSphere(targetBallName);
		removeSphere(startBallName);

		setCurrentPath(null);
		setTemporaryPath(null);

		lastStartPointSet = false;
		setPathUnfinished(false);

		updateAllViewers();
	}

	/**
	 * Automatically traces a path from a point A to a point B. See
	 * {@link #autoTrace(List, PointInImage)} for details.
	 *
	 * @param start the {@link PointInImage} the starting point of the path
	 * @param end the {@link PointInImage} the terminal point of the path
	 * @param forkPoint the {@link PointInImage} fork point of the parent
	 *          {@link Path} from which the searched path should branch off, or
	 *          null if the the path should not have any parent.
	 * @return the path a reference to the computed path.
	 * @see #autoTrace(List, PointInImage)
	 */
	public Path autoTrace(final PointInImage start, final PointInImage end,
		final PointInImage forkPoint)
	{
		final ArrayList<PointInImage> list = new ArrayList<>();
		list.add(start);
		list.add(end);
		return autoTrace(list, forkPoint);
	}

	/**
	 * Automatically traces a path from a list of points and adds it to the active
	 * {@link PathAndFillManager} instance. Note that this method still requires
	 * SNT's UI. For headless auto-tracing have a look at {@link TracerThread}.
	 * <p>
	 * SNT's UI will remain blocked in "search mode" until the Path computation
	 * completes. Tracing occurs through the active {@link SearchInterface}
	 * selected in the UI, i.e., {@link TracerThread} (the default A* search),
	 * {@link TubularGeodesicsTracer}, etc.
	 * <p>
	 * All input {@link PointInImage} must be specified in real world coordinates.
	 * <p>
	 *
	 * @param pointList the list of {@link PointInImage} containing the nodes to
	 *          be used as target goals during the search. If the search cannot
	 *          converge into a target point, such point is omitted from path, if
	 *          Successful, target point will be included in the final path. the
	 *          final path. The first point in the list is the start of the path,
	 *          the last its terminus. Null objects not allowed.
	 * @param forkPoint the {@link PointInImage} fork point of the parent
	 *          {@link Path} from which the searched path should branch off, or
	 *          null if the the path should not have any parent.
	 * @return the path a reference to the computed path. It is added to the Path
	 *         Manager list.If a path cannot be fully computed from the specified
	 *         list of points, a single-point path is generated.
	 */
	public Path autoTrace(final List<PointInImage> pointList,
		final PointInImage forkPoint)
	{
		if (pointList == null || pointList.size() == 0)
			throw new IllegalArgumentException("pointList cannot be null or empty");

		// Set UI. Ensure there are no incomplete tracings around
		final boolean cfTemp = getUI().confirmTemporarySegments;
		if (getUIState() != SNTUI.WAITING_TO_START_PATH) getUI()
			.abortCurrentOperation();
		getUI().confirmTemporarySegments = false;

		// Start path from first point in list
		final PointInImage start = pointList.get(0);
		startPath(start.x, start.y, start.z, forkPoint);

		final int secondNodeIdx = (pointList.size() == 1) ? 0 : 1;
		final int nNodes = pointList.size();

		// Now keep appending nodes to temporary path
		for (int i = secondNodeIdx; i < nNodes; i++) {

			try {
				// Block and update UI
				changeUIState(SNTUI.SEARCHING);
				showStatus(i, nNodes, "Finding path to node " + i + "/" + nNodes);

				// Append node and wait for search to be finished
				final PointInImage node = pointList.get(i);
				testPathTo(node.x, node.y, node.z, null);
				((Thread) getActiveSearchingThread()).join();

			}
			catch (final NullPointerException ex) {
				// do nothing: search thread has already terminated or node is null
			}
			catch (final InterruptedException ex) {
				showStatus(0, 0, "Search interrupted!");
				SNT.error("Search interrupted", ex);
			}
			catch (final ArrayIndexOutOfBoundsException
					| IllegalArgumentException ex)
			{
				// It is likely that search failed for this node. These will be
				// triggered if
				// e.g., point- is out of image bounds,
				showStatus(i, nNodes, "ERROR: Search failed!...");
				SNT.error("Search failed for node " + i);
				// continue;
			}
			finally {
				showStatus(i, pointList.size(), "Confirming segment...");
				confirmTemporary();
			}
		}
		finishedPath();

		// restore UI state
		showStatus(0, 0, "Tracing Complete");
		getUI().confirmTemporarySegments = cfTemp;

		return pathAndFillManager.getPath(pathAndFillManager.size() - 1);
	}

	private SearchInterface getActiveSearchingThread() {
		if (manualSearchThread != null) return manualSearchThread;
		if (tubularGeodesicsThread != null) return tubularGeodesicsThread;
		return currentSearchThread;
	}

	synchronized protected void replaceCurrentPath(final Path path) {
		if (currentPath != null) {
			discreteMsg("An active temporary path already exists...");
			return;
		}
//		if (getUIState() != SNTUI.WAITING_TO_START_PATH) {
//			discreteMsg("Please finish current operation before extending "+ path.getName());
//			return;
//		}
		unsavedPaths = true;
		lastStartPointSet = true;
		selectPath(path, false);
		setPathUnfinished(true);
		setCurrentPath(path);
		last_start_point_x = (int) Math.round(path.lastPoint().x / x_spacing);
		last_start_point_y = (int) Math.round(path.lastPoint().y / y_spacing);
		last_start_point_z = (int) Math.round(path.lastPoint().z / z_spacing);
		setTemporaryPath(null);
		changeUIState(SNTUI.PARTIAL_PATH);
		updateAllViewers();
	}

	synchronized protected void finishedPath() {

		if (currentPath == null) {
			// this can happen through repeated hotkey presses
			discreteMsg("No temporary path to finish...");
			return;
		}

		// Is there an unconfirmed path? If so, confirm it first
		if (temporaryPath != null) confirmTemporary();

		if (justFirstPoint() && ui.confirmTemporarySegments && !getConfirmation(
			"Create a single point path? (such path is typically used to mark the cell soma)",
			"Create Single Point Path?"))
		{
			return;
		}

		if (justFirstPoint()) {
			final PointInImage p = new PointInImage(last_start_point_x * x_spacing,
				last_start_point_y * y_spacing, last_start_point_z * z_spacing);
			currentPath.addPointDouble(p.x, p.y, p.z);
			currentPath.endJoinsPoint = p;
			currentPath.startJoinsPoint = p;
			cancelSearch(false);
		}
		else {
			removeSphere(startBallName);
		}

		removeSphere(targetBallName);
		if (pathAndFillManager.getPathFromID(currentPath.getID()) == null)
			pathAndFillManager.addPath(currentPath, true);
		unsavedPaths = true;
		lastStartPointSet = false;
		selectPath(currentPath, false);
		setPathUnfinished(false);
		setCurrentPath(null);

		// ... and change the state of the UI
		changeUIState(SNTUI.WAITING_TO_START_PATH);
		updateAllViewers();
	}

	synchronized public void clickForTrace(final Point3d p, final boolean join) {
		final double x_unscaled = p.x / x_spacing;
		final double y_unscaled = p.y / y_spacing;
		final double z_unscaled = p.z / z_spacing;
		setZPositionAllPanes((int) x_unscaled, (int) y_unscaled, (int) z_unscaled);
		clickForTrace(p.x, p.y, p.z, join);
	}

	synchronized public void clickForTrace(final double world_x,
		final double world_y, final double world_z, final boolean join)
	{

		PointInImage joinPoint = null;

		if (join) {
			joinPoint = pathAndFillManager.nearestJoinPointOnSelectedPaths(world_x /
				x_spacing, world_y / y_spacing, world_z / z_spacing);
		}

		if (ui == null) return;

		// FIXME: in some of the states this doesn't make sense; check for them:

		if (currentSearchThread != null) return;

		if (temporaryPath != null) return;

		if (filler != null) {
			setFillThresholdFrom(world_x, world_y, world_z);
			return;
		}

		if (pathUnfinished) {
			/*
			 * Then this is a succeeding point, and we should start a search.
			 */
			testPathTo(world_x, world_y, world_z, joinPoint);
			changeUIState(SNTUI.SEARCHING);
		}
		else {
			/* This is an initial point. */
			startPath(world_x, world_y, world_z, joinPoint);
			changeUIState(SNTUI.PARTIAL_PATH);
		}

	}

	synchronized public void clickForTrace(final double x_in_pane_precise,
		final double y_in_pane_precise, final int plane, final boolean join)
	{

		final double[] p = new double[3];
		findPointInStackPrecise(x_in_pane_precise, y_in_pane_precise, plane, p);

		final double world_x = p[0] * x_spacing;
		final double world_y = p[1] * y_spacing;
		final double world_z = p[2] * z_spacing;

		clickForTrace(world_x, world_y, world_z, join);
	}

	public void setFillThresholdFrom(final double world_x, final double world_y,
		final double world_z)
	{

		final float distance = filler.getDistanceAtPoint(world_x / x_spacing,
			world_y / y_spacing, world_z / z_spacing);

		setFillThreshold(distance);
	}

	public void setFillThreshold(final double distance) {
		if (!Double.isNaN(distance) && distance > 0) {
			SNT.log("Setting new threshold of: " + distance);
			if (ui != null) ui.thresholdChanged(distance);
			filler.setThreshold(distance);
		}
	}

	synchronized void startPath(final double world_x, final double world_y,
		final double world_z, final PointInImage joinPoint)
	{

		endJoin = null;
		endJoinPoint = null;

		if (lastStartPointSet) {
			statusService.showStatus(
				"The start point has already been set; to finish a path press 'F'");
			return;
		}

		setPathUnfinished(true);
		lastStartPointSet = true;

		final Path path = new Path(x_spacing, y_spacing, z_spacing, spacing_units);
		path.setCTposition(channel, frame);
		path.setName("New Path");

		Color ballColor;

		double real_last_start_x, real_last_start_y, real_last_start_z;

		if (joinPoint == null) {
			real_last_start_x = world_x;
			real_last_start_y = world_y;
			real_last_start_z = world_z;
			ballColor = getXYCanvas().getTemporaryPathColor();
		}
		else {
			real_last_start_x = joinPoint.x;
			real_last_start_y = joinPoint.y;
			real_last_start_z = joinPoint.z;
			path.setStartJoin(joinPoint.onPath, joinPoint);
			ballColor = Color.GREEN;
		}

		last_start_point_x = real_last_start_x / x_spacing;
		last_start_point_y = real_last_start_y / y_spacing;
		last_start_point_z = real_last_start_z / z_spacing;

		addSphere(startBallName, real_last_start_x, real_last_start_y,
			real_last_start_z, ballColor, x_spacing * ballRadiusMultiplier);

		setCurrentPath(path);
	}

	protected void addSphere(final String name, final double x, final double y,
		final double z, final Color color, final double radius)
	{
		if (use3DViewer) {
			final List<Point3f> sphere = customnode.MeshMaker.createSphere(x, y, z,
				radius);
			univ.addTriangleMesh(sphere, new Color3f(color), name);
		}
	}

	protected void removeSphere(final String name) {
		if (use3DViewer) univ.removeContent(name);
	}

	/*
	 * Return true if we have just started a new path, but have not yet added any
	 * connections to it, otherwise return false.
	 */
	private boolean justFirstPoint() {
		return pathUnfinished && (currentPath.size() == 0);
	}

	protected void startSholl(final PointInImage centerScaled) {
		setZPositionAllPanes((int) Math.round(centerScaled.x), (int) Math.round(
			centerScaled.y), (int) Math.round(centerScaled.z));
		setShowOnlySelectedPaths(false);
		SNT.log("Starting Sholl Analysis centered at " + centerScaled);
		final Map<String, Object> input = new HashMap<>();
		input.put("snt", this);
		input.put("center", centerScaled);
		final Tree tree = new Tree(getPathAndFillManager().getPathsFiltered());
		input.put("tree", tree);
		final CommandService cmdService = getContext().getService(
			CommandService.class);
		cmdService.run(ShollTracingsCmd.class, true, input);
	}

	public void viewFillIn3D(final boolean asMask) {
		if (filler == null) return;
		final ImagePlus imagePlus = filler.fillAsImagePlus(!asMask);
		imagePlus.show();
	}

	public int guessResamplingFactor() {
		if (width == 0 || height == 0 || depth == 0) throw new RuntimeException(
			"Can't call guessResamplingFactor() before width, height and depth are set...");
		/*
		 * This is about right for me, but probably should be related to the free memory
		 * somehow. However, those calls are so notoriously unreliable on Java that it's
		 * probably not worth it.
		 */
		final long maxSamplePoints = 500 * 500 * 100;
		int level = 0;
		while (true) {
			final long samplePoints = (long) (width >> level) *
				(long) (height >> level) * (depth >> level);
			if (samplePoints < maxSamplePoints) return (1 << level);
			++level;
		}
	}

	protected boolean isUIready() {
		if (ui == null) return false;
		return ui.isVisible();
	}

	public void launchPaletteAround(final int x, final int y, final int z) {

		final int either_side = 40;

		int x_min = x - either_side;
		int x_max = x + either_side;
		int y_min = y - either_side;
		int y_max = y + either_side;
		int z_min = z - either_side;
		int z_max = z + either_side;

		final int originalWidth = xy.getWidth();
		final int originalHeight = xy.getHeight();
		final int originalDepth = xy.getNSlices();

		if (x_min < 0) x_min = 0;
		if (y_min < 0) y_min = 0;
		if (z_min < 0) z_min = 0;
		if (x_max >= originalWidth) x_max = originalWidth - 1;
		if (y_max >= originalHeight) y_max = originalHeight - 1;
		if (z_max >= originalDepth) z_max = originalDepth - 1;

		final double[] sigmas = new double[9];
		for (int i = 0; i < sigmas.length; ++i) {
			sigmas[i] = ((i + 1) * getMinimumSeparation()) / 2;
		}

		ui.changeState(SNTUI.WAITING_FOR_SIGMA_CHOICE);

		final SigmaPalette sp = new SigmaPalette();
		sp.setListener(ui.listener);
		sp.makePalette(getLoadedDataAsImp(), x_min, x_max, y_min, y_max, z_min,
			z_max, new TubenessProcessor(true), sigmas, 256 / ui.getMultiplier(), 3,
			3, z);
	}

	public void startFillerThread(final FillerThread filler) {

		this.filler = filler;

		filler.addProgressListener(this);
		filler.addProgressListener(ui.getFillManager());

		addThreadToDraw(filler);

		filler.start();

		ui.changeState(SNTUI.FILLING_PATHS);

	}

	synchronized public void startFillingPaths(final Set<Path> fromPaths) {

		// currentlyFilling = true;
		ui.getFillManager().pauseOrRestartFilling.setText("Pause");
		ui.getFillManager().thresholdChanged(0.03f);
		filler = new FillerThread(xy, stackMin, stackMax, false, // startPaused
			true, // reciprocal
			0.03f, // Initial threshold to display
			5000); // reportEveryMilliseconds

		addThreadToDraw(filler);

		filler.addProgressListener(this);
		filler.addProgressListener(ui.getFillManager());

		filler.setSourcePaths(fromPaths);

		ui.setFillListVisible(true);

		filler.start();

		ui.changeState(SNTUI.FILLING_PATHS);

	}

	public void setFillTransparent(final boolean transparent) {
		xy_tracer_canvas.setFillTransparent(transparent);
		if (!single_pane) {
			xz_tracer_canvas.setFillTransparent(transparent);
			zy_tracer_canvas.setFillTransparent(transparent);
		}
	}

	public double getMinimumSeparation() {
		return Math.min(Math.abs(x_spacing), Math.min(Math.abs(y_spacing), Math.abs(
			z_spacing)));
	}

	/**
	 * Retrieves the pixel data currently loaded in memory as an ImagePlus object.
	 * Returned image is always a single channel image. The main purpose of this
	 * method is to bridge SNT with other legacy classes that cannot deal with
	 * multidimensional images.
	 *
	 * @return the loaded data corresponding to the C,T position currently being
	 *         traced, or null if no image data has been loaded into memory.
	 */
	public ImagePlus getLoadedDataAsImp() {
		if (!inputImageLoaded()) return null;
		final ImageStack stack = new ImageStack(xy.getWidth(), xy.getHeight());
		switch (imageType) {
			case ImagePlus.GRAY8:
			case ImagePlus.COLOR_256:
				for (int z = 0; z < depth; ++z) {
					final ImageProcessor ip = new ByteProcessor(xy.getWidth(), xy
						.getHeight());
					ip.setPixels(slices_data_b[z]);
					stack.addSlice(ip);
				}
				break;
			case ImagePlus.GRAY16:
				for (int z = 0; z < depth; ++z) {
					final ImageProcessor ip = new ShortProcessor(xy.getWidth(), xy
						.getHeight());
					ip.setPixels(slices_data_s[z]);
					stack.addSlice(ip);
				}
				break;
			case ImagePlus.GRAY32:
				for (int z = 0; z < depth; ++z) {
					final ImageProcessor ip = new FloatProcessor(xy.getWidth(), xy
						.getHeight());
					ip.setPixels(slices_data_f[z]);
					stack.addSlice(ip);
				}
				break;
			default:
				throw new IllegalArgumentException("Bug: unsupported type somehow");
		}
		final ImagePlus imp = new ImagePlus("C" + channel + "F" + frame, stack);
		updateLut(); // If the LUT meanwhile changed, update it
		imp.setLut(lut); // ignored if null
		imp.copyScale(xy);
		return imp;
	}

	public void startHessian() {
		if (hessian == null) {
			ui.changeState(SNTUI.CALCULATING_GAUSSIAN);
			hessianSigma = ui.getSigma();
			hessian = new ComputeCurvatures(getLoadedDataAsImp(), hessianSigma, this,
				true);
			new Thread(hessian).start();
		}
		else {
			final double newSigma = ui.getSigma();
			if (newSigma != hessianSigma) {
				ui.changeState(SNTUI.CALCULATING_GAUSSIAN);
				hessianSigma = newSigma;
				hessian = new ComputeCurvatures(getLoadedDataAsImp(), hessianSigma,
					this, true);
				new Thread(hessian).start();
			}
		}
	}

	/**
	 * Specifies the "filtered" image to be used during a tracing session.
	 *
	 * @param file The file containing the "filtered" image (typically named
	 *          {@code <image-basename>.tubes.tif} (tubeness),
	 *          {@code <image-basename>.frangi.tif} (Frangi Vesselness), or
	 *          {@code <image-basename>.oof.tif} (tubular geodesics))
	 */
	public void setFilteredImage(final File file) {
		filteredFileImage = file;
	}

	/**
	 * Returns the file of the 'filtered' image, if any.
	 *
	 * @return the filtered image file, or null if no file has been set
	 */
	public File getFilteredImage() {
		return filteredFileImage;
	}

	/**
	 * Assesses if a 'filtered image' has been loaded into memory. Note that while
	 * some tracers will load the image into memory, others may waive the loading
	 * to third party libraries
	 *
	 * @return true, if image has been loaded into memory.
	 */
	public boolean filteredImageLoaded() {
		return filteredData != null;
	}

	protected boolean inputImageLoaded() {
		return slices_data_b != null || slices_data_s != null || slices_data_f != null;
	}

	protected boolean isTracingOnFilteredImageAvailable() {
		return filteredImageLoaded() || tubularGeodesicsTracingEnabled;
	}

	/**
	 * loads the 'filtered image' specified by {@link #setFilteredImage(File) into
	 * memory as 32-bit data}.
	 *
	 * @throws IOException If image could not be loaded
	 * @throws IllegalArgumentException if path specified through
	 *           {@link #setFilteredImage(File)} is invalid
	 */
	public void loadFilteredImage() throws IOException, IllegalArgumentException {
		if (xy == null) throw new IllegalArgumentException(
			"Data can only be loaded after main tracing image is known");
		if (!SNT.fileAvailable(filteredFileImage))
			throw new IllegalArgumentException("File path of input data unknown");
		final Dataset ds = datasetIOService.open(filteredFileImage
			.getAbsolutePath());
		final int bitsPerPix = ds.getType().getBitsPerPixel();
		final StringBuilder sBuilder = new StringBuilder();
		if (bitsPerPix != 32) sBuilder.append("Not a 32-bit image. ");
		if (ds.dimension(Axes.CHANNEL) > 1 || ds.dimension(Axes.TIME) > 1) sBuilder
			.append("Too many dimensions: C,T images are not supported. ");
		if (ds.getWidth() != xy.getWidth() || ds.getHeight() != xy.getHeight() || ds
			.getDepth() != xy.getNSlices()) sBuilder.append("XYZ Dimensions do not match those of ").append(xy.getTitle()).append(".");
		if (!sBuilder.toString().isEmpty()) throw new IllegalArgumentException(
			sBuilder.toString());

		statusService.showStatus("Loading alternative tracing images");
		final ImagePlus imp = convertService.convert(ds, ImagePlus.class);
		final ImageStack s = imp.getStack();
		filteredData = new float[depth][];
		for (int z = 0; z < depth; ++z) {
			statusService.showStatus(z, depth, "Loading stack...");
			filteredData[z] = (float[]) s.getPixels(z + 1);
		}
		statusService.clearStatus();
	}

	public synchronized void enableHessian(final boolean enable) {
		hessianEnabled = enable;
		if (enable) startHessian();
	}

	public synchronized void cancelGaussian() {
		if (hessian != null) {
			hessian.cancelGaussianGeneration();
		}
	}

	// This is the implementation of GaussianGenerationCallback
	@Override
	public void proportionDone(final double proportion) {
		if (proportion < 0) {
			hessianEnabled = false;
			hessian = null;
			hessianSigma = -1;
			ui.gaussianCalculated(false);
			statusService.showProgress(1, 1);
			return;
		}
		else if (proportion >= 1.0) {
			hessianEnabled = true;
			ui.gaussianCalculated(true);
		}
		statusService.showProgress((int) proportion, 1); // FIXME:
	}

	@Deprecated
	public void showCorrespondencesTo(final File tracesFile, final Color c,
		final double maxDistance)
	{

		final PathAndFillManager pafmTraces = new PathAndFillManager(this);
		if (!pafmTraces.load(tracesFile.getAbsolutePath())) {
			guiUtils.error("Failed to load traces from: " + tracesFile
				.getAbsolutePath());
			return;
		}

		final List<Point3f> linePoints = new ArrayList<>();

		// Now find corresponding points from the first one, and draw lines to
		// them:
		final List<NearPoint> cp = pathAndFillManager.getCorrespondences(pafmTraces,
			maxDistance);
		int done = 0;
		for (final NearPoint np : cp) {
			if (np != null) {
				// SNT.log("Drawing:");
				// SNT.log(np.toString());

				linePoints.add(new Point3f((float) np.near.x, (float) np.near.y,
					(float) np.near.z));
				linePoints.add(new Point3f((float) np.closestIntersection.x,
					(float) np.closestIntersection.y, (float) np.closestIntersection.z));

				final String ballName = univ.getSafeContentName("ball " + done);
				final List<Point3f> sphere = customnode.MeshMaker.createSphere(
					np.near.x, np.near.y, np.near.z, Math.abs(x_spacing / 2));
				univ.addTriangleMesh(sphere, new Color3f(c), ballName);
			}
			++done;
		}
		univ.addLineMesh(linePoints, new Color3f(Color.RED), "correspondences",
			false);

		for (int pi = 0; pi < pafmTraces.size(); ++pi) {
			final Path p = pafmTraces.getPath(pi);
			if (p.getUseFitted()) continue;
			p.addAsLinesTo3DViewer(univ, c, null);
		}
		// univ.resetView();
	}

	protected void setShowOnlySelectedPaths(final boolean showOnlySelectedPaths,
		final boolean updateGUI)
	{
		this.showOnlySelectedPaths = showOnlySelectedPaths;
		if (updateGUI) {
			updateAllViewers();
		}
	}

	protected void setShowOnlyActiveCTposPaths(
		final boolean showOnlyActiveCTposPaths, final boolean updateGUI)
	{
		this.showOnlyActiveCTposPaths = showOnlyActiveCTposPaths;
		if (updateGUI) {
			updateAllViewers();
		}
	}

	public void setShowOnlySelectedPaths(final boolean showOnlySelectedPaths) {
		setShowOnlySelectedPaths(showOnlySelectedPaths, true);
	}

	protected StackWindow getWindow(final int plane) {
		switch (plane) {
			case MultiDThreePanes.XY_PLANE:
				return xy_window;
			case MultiDThreePanes.XZ_PLANE:
				return (single_pane) ? null : xz_window;
			case MultiDThreePanes.ZY_PLANE:
				return (single_pane) ? null : zy_window;
			default:
				return null;
		}
	}

	/**
	 * Gets the Image associated with a view pane.
	 *
	 * @param pane the flag specifying the view either
	 *          {@link MultiDThreePanes#XY_PLANE},
	 *          {@link MultiDThreePanes#XZ_PLANE} or
	 *          {@link MultiDThreePanes#ZY_PLANE}.
	 * @return the image associate with the specified view, or null if the view is
	 *         not available
	 */
	public ImagePlus getImagePlus(final int pane) {
		ImagePlus imp = null;
		switch (pane) {
			case XY_PLANE:
				if (xy != null && isDummy()) return null;
				imp = xy;
				break;
			case XZ_PLANE:
				imp = xz;
				break;
			case ZY_PLANE:
				imp = zy;
				break;
			default:
				break;
		}
		return (imp == null || imp.getProcessor() == null) ? null : imp;
	}

	@Override
	public void error(final String msg) {
		new GuiUtils(getActiveWindow()).error(msg);
	}

	public void showMsg(final String msg, final String title) {
		new GuiUtils(getActiveWindow()).error(msg, title);
	}

	private Component getActiveCanvas() {
		if (!isUIready()) return null;
		final List<Component> components = new ArrayList<>();
		components.add(xy_canvas);
		components.add(xz_canvas);
		components.add(zy_canvas);
		if (univ != null) components.add(univ.getCanvas());
		for (final Component c : components) {
			if (c != null && c.isFocusOwner()) return c;
		}
		return null;
	}

	private Component getActiveWindow() {
		if (!isUIready()) return null;
		if (ui.isActive()) return ui;
		final Window[] images = { xy_window, xz_window, zy_window };
		for (final Window win : images) {
			if (win != null && win.isActive()) return win;
		}
		final Window[] frames = { ui.getPathManager(), ui.getFillManager() };
		for (final Window frame : frames) {
			if (frame.isActive()) return frame;
		}
		return ui.recViewerFrame;
	}

	public boolean isOnlySelectedPathsVisible() {
		return showOnlySelectedPaths;
	}

	public void updateAllViewers() {
		repaintAllPanes();
		update3DViewerContents();
		if (getUI() != null && getUI().recViewer != null) {
			new Thread(() -> {
				getUI().recViewer.syncPathManagerList();
			}).start();
		}
		if (getUI() != null && getUI().sciViewSNT != null) {
			new Thread(() -> {
				getUI().sciViewSNT.syncPathManagerList();
			}).start();
		}
	}

	/*
	 * Whatever the state of the paths, update the 3D viewer to make sure that
	 * they're the right colour, the right version (fitted or unfitted) is being
	 * used and whether the path should be displayed at all - it shouldn't if the
	 * "Show only selected paths" option is set.
	 */
	@Deprecated
	public void update3DViewerContents() {
		if (use3DViewer && univ != null) {
			new Thread(() -> {
				pathAndFillManager.update3DViewerContents();
			}).start();
		}
	}

	/**
	 * Gets the instance of the legacy 3D viewer universe. Note that the legacy 3D
	 * viewer is now deprecated.
	 *
	 * @return the a reference to the 3DUniverse or null if no universe has been
	 *         set
	 */
	@Deprecated
	public Image3DUniverse get3DUniverse() {
		return univ;
	}

	public void set3DUniverse(final Image3DUniverse universe) {
		univ = universe;
		use3DViewer = universe != null;
		if (use3DViewer) {
			// ensure there are no duplicated listeners
			univ.removeUniverseListener(pathAndFillManager);
			univ.addUniverseListener(pathAndFillManager);
			update3DViewerContents();
		}
	}

	@Deprecated
	public void updateImageContent(final int resamplingFactor) {
		if (univ == null || xy == null) return;

		new Thread(() -> {

			// The legacy 3D viewer works only with 8-bit or RGB images
			final ImagePlus loadedImp = getLoadedDataAsImp();
			ContentCreator.convert(loadedImp);
			final String cTitle = xy.getTitle() + "[C=" + channel + " T=" + frame +
				"]";
			final Content c = ContentCreator.createContent( //
				univ.getSafeContentName(cTitle), // unique descriptor
				loadedImp, // grayscale image
				ContentConstants.VOLUME, // rendering option
				resamplingFactor, // resampling factor
				0, // time point: loadedImp does not have T dimension
				null, // new Color3f(Color.WHITE), // Default color
				Content.getDefaultThreshold(loadedImp, ContentConstants.VOLUME), // threshold
				new boolean[] { true, true, true } // displayed channels
			);

			c.setTransparency(0.5f);
			c.setLocked(true);
			if (imageContent != null) {
				univ.removeContent(imageContent.getName());
			}
			imageContent = c;
			univ.addContent(c);
			univ.setAutoAdjustView(false);
		}).start();
	}

	public void setSelectedColor(final Color newColor) {
		selectedColor = newColor;
		selectedColor3f = new Color3f(newColor);
		updateAllViewers();
	}

	public void setDeselectedColor(final Color newColor) {
		deselectedColor = newColor;
		deselectedColor3f = new Color3f(newColor);
		if (getUI() != null && getUI().recViewer != null) {
			getUI().recViewer.setDefaultColor(new ColorRGB(newColor.getRed(), newColor
				.getGreen(), newColor.getBlue()));
		}
		updateAllViewers();
	}

	/*
	 * FIXME: this can be very slow ... Perhaps do it in a separate thread?
	 */
	@Deprecated
	public void setColorImage(final ImagePlus newColorImage) {
		colorImage = newColorImage;
		update3DViewerContents();
	}

	private int paths3DDisplay = 1;

	@Deprecated
	public void setPaths3DDisplay(final int paths3DDisplay) {
		this.paths3DDisplay = paths3DDisplay;
		update3DViewerContents();
	}

	@Deprecated
	public int getPaths3DDisplay() {
		return this.paths3DDisplay;
	}

	public void selectPath(final Path p, final boolean addToExistingSelection) {
		final HashSet<Path> pathsToSelect = new HashSet<>();
		if (p.isFittedVersionOfAnotherPath()) pathsToSelect.add(p.fittedVersionOf);
		else pathsToSelect.add(p);
		if (isEditModeEnabled()) { // impose a single editing path
			ui.getPathManager().setSelectedPaths(pathsToSelect, this);
			setEditingPath(p);
			return;
		}
		if (addToExistingSelection) {
			pathsToSelect.addAll(ui.getPathManager().getSelectedPaths(false));
		}
		ui.getPathManager().setSelectedPaths(pathsToSelect, this);
	}

	public Collection<Path> getSelectedPaths() {
		if (ui.getPathManager() != null) {
			return ui.getPathManager().getSelectedPaths(false);
		}
		throw new IllegalArgumentException(
			"getSelectedPaths was called when PathManagerUI was null");
	}

	@Override
	public void setPathList(final String[] newList, final Path justAdded,
		final boolean expandAll)
	{}

	@Override
	public void setFillList(final String[] newList) {}

	// Note that rather unexpectedly the p.setSelcted calls make sure that
	// the colour of the path in the 3D viewer is right... (FIXME)
	@Override
	public void setSelectedPaths(final HashSet<Path> selectedPathsSet,
		final Object source)
	{
		if (source == this) return;
		for (int i = 0; i < pathAndFillManager.size(); ++i) {
			final Path p = pathAndFillManager.getPath(i);
			if (selectedPathsSet.contains(p)) {
				p.setSelected(true);
			}
			else {
				p.setSelected(false);
			}
		}
	}

	/**
	 * This method will: 1) remove the existing {@link KeyListener}s from the
	 * component 'c'; 2) instruct 'firstKeyListener' to call those KeyListener if
	 * it has not dealt with the key; and 3) set 'firstKeyListener' as the
	 * KeyListener for 'c'.
	 *
	 * @param c the Component to which the Listener should be attached
	 * @param firstKeyListener the first key listener
	 */
	private static void setAsFirstKeyListener(final Component c,
		final QueueJumpingKeyListener firstKeyListener)
	{
		if (c == null) return;
		final KeyListener[] oldKeyListeners = c.getKeyListeners();
		for (final KeyListener kl : oldKeyListeners) {
			c.removeKeyListener(kl);
		}
		firstKeyListener.addOtherKeyListeners(oldKeyListeners);
		c.addKeyListener(firstKeyListener);
		setAsFirstKeyListener(c.getParent(), firstKeyListener);
	}

	public synchronized void findSnappingPointInXYview(final double x_in_pane,
		final double y_in_pane, final double[] point)
	{

		// if (width == 0 || height == 0 || depth == 0)
		// throw new RuntimeException(
		// "Can't call findSnappingPointInXYview() before width, height and
		// depth are set...");

		final int[] window_center = new int[3];
		findPointInStack((int) Math.round(x_in_pane), (int) Math.round(y_in_pane),
			MultiDThreePanes.XY_PLANE, window_center);
		int startx = window_center[0] - cursorSnapWindowXY;
		if (startx < 0) startx = 0;
		int starty = window_center[1] - cursorSnapWindowXY;
		if (starty < 0) starty = 0;
		int startz = window_center[2] - cursorSnapWindowZ;
		if (startz < 0) startz = 0;
		int stopx = window_center[0] + cursorSnapWindowXY;
		if (stopx > width) stopx = width;
		int stopy = window_center[1] + cursorSnapWindowXY;
		if (stopy > height) stopy = height;
		int stopz = window_center[2] + cursorSnapWindowZ;
		if (cursorSnapWindowZ == 0) {
			++stopz;
		}
		else if (stopz > depth) {
			stopz = depth;
		}

		ArrayList<int[]> pointsAtMaximum = new ArrayList<>();
		float currentMaximum = -Float.MAX_VALUE;
		for (int x = startx; x < stopx; ++x) {
			for (int y = starty; y < stopy; ++y) {
				for (int z = startz; z < stopz; ++z) {
					float v = -Float.MAX_VALUE;
					final int xyIndex = y * width + x;
					switch (imageType) {
						case ImagePlus.GRAY8:
						case ImagePlus.COLOR_256:
							v = 0xFF & slices_data_b[z][xyIndex];
							break;
						case ImagePlus.GRAY16:
							v = slices_data_s[z][xyIndex];
							break;
						case ImagePlus.GRAY32:
							v = slices_data_f[z][xyIndex];
							break;
						default:
							throw new RuntimeException("Unknow image type: " + imageType);
					}
					if (v > currentMaximum) {
						pointsAtMaximum = new ArrayList<>();
						pointsAtMaximum.add(new int[] { x, y, z });
						currentMaximum = v;
					}
					else if (v == currentMaximum) {
						pointsAtMaximum.add(new int[] { x, y, z });
					}
				}
			}
		}

		// if (pointsAtMaximum.size() == 0) {
		// findPointInStackPrecise(x_in_pane, y_in_pane, ThreePanes.XY_PLANE,
		// point);
		// if (verbose)
		// SNT.log("No maxima in snap-to window");
		// return;
		// }

		final int[] snapped_p = pointsAtMaximum.get(pointsAtMaximum.size() / 2);
		if (window_center[2] != snapped_p[2]) xy.setZ(snapped_p[2] + 1);
		point[0] = snapped_p[0];
		point[1] = snapped_p[1];
		point[2] = snapped_p[2];
	}

	public void clickAtMaxPointInMainPane(final int x_in_pane,
		final int y_in_pane)
	{
		clickAtMaxPoint(x_in_pane, y_in_pane, MultiDThreePanes.XY_PLANE);
	}

	public void clickAtMaxPoint(final int x_in_pane, final int y_in_pane,
		final int plane)
	{
		final int[][] pointsToConsider = findAllPointsAlongLine(x_in_pane,
			y_in_pane, plane);
		ArrayList<int[]> pointsAtMaximum = new ArrayList<>();
		float currentMaximum = -Float.MAX_VALUE;
		for (int[] ints : pointsToConsider) {
			float v = -Float.MAX_VALUE;
			final int[] p = ints;
			final int xyIndex = p[1] * width + p[0];
			switch (imageType) {
				case ImagePlus.GRAY8:
				case ImagePlus.COLOR_256:
					v = 0xFF & slices_data_b[p[2]][xyIndex];
					break;
				case ImagePlus.GRAY16:
					v = slices_data_s[p[2]][xyIndex];
					break;
				case ImagePlus.GRAY32:
					v = slices_data_f[p[2]][xyIndex];
					break;
				default:
					throw new RuntimeException("Unknow image type: " + imageType);
			}
			if (v > currentMaximum) {
				pointsAtMaximum = new ArrayList<>();
				pointsAtMaximum.add(p);
				currentMaximum = v;
			}
			else if (v == currentMaximum) {
				pointsAtMaximum.add(p);
			}
		}
		/*
		 * Take the middle of those points, and pretend that was the point that was
		 * clicked on.
		 */
		final int[] p = pointsAtMaximum.get(pointsAtMaximum.size() / 2);

		clickForTrace(p[0] * x_spacing, p[1] * y_spacing, p[2] * z_spacing, false);
	}

	private ImagePlus[] getLoadedDataGray8Panes() {

		final ImagePlus xy8 = getLoadedDataAsImp();
		ImagePlus xz8 = null;
		ImagePlus zy8 = null;
		if (xy8.getType() != ImagePlus.GRAY8) {
			final boolean doScaling = ImageConverter.getDoScaling();
			ImageConverter.setDoScaling(true);
			new ImageConverter(xy8).convertToGray8();
			ImageConverter.setDoScaling(doScaling);
		}

		if (!single_pane) {

			final ImageStack xyStack = xy8.getStack();
			final byte[][] slicesData = new byte[depth][];
			for (int z = 1; z <= depth; ++z)
				slicesData[z - 1] = (byte[]) xyStack.getPixels(z);

			// Create the ZY slices:
			final ImageStack zy_stack = new ImageStack(depth, height);
			for (int x_in_original = 0; x_in_original < width; ++x_in_original) {
				final byte[] sliceBytes = new byte[depth * height];
				for (int z_in_original = 0; z_in_original < depth; ++z_in_original) {
					for (int y_in_original = 0; y_in_original < height; ++y_in_original) {
						final int x_in_left = z_in_original;
						final int y_in_left = y_in_original;
						sliceBytes[y_in_left * depth + x_in_left] =
							slicesData[z_in_original][y_in_original * width + x_in_original];
					}
				}

				final ByteProcessor bp = new ByteProcessor(depth, height);
				bp.setPixels(sliceBytes);
				zy_stack.addSlice(null, bp);
			}

			zy8 = new ImagePlus("ZY 8-bit", zy_stack);
			zy8.setLut(lut);
			final Calibration zyCal = xy.getCalibration().copy();
			zyCal.pixelWidth = xy8.getCalibration().pixelDepth;
			zyCal.pixelDepth = xy8.getCalibration().pixelWidth;
			zy8.setCalibration(zyCal);

			// Create the XZ slices:
			final ImageStack xz_stack = new ImageStack(width, depth);
			for (int y_in_original = 0; y_in_original < height; ++y_in_original) {
				final byte[] sliceBytes = new byte[width * depth];
				for (int z_in_original = 0; z_in_original < depth; ++z_in_original) {
					final int y_in_top = z_in_original;
					System.arraycopy(slicesData[z_in_original], y_in_original * width,
						sliceBytes, y_in_top * width, width);
				}
				final ByteProcessor bp = new ByteProcessor(width, depth);
				bp.setPixels(sliceBytes);
				xz_stack.addSlice(null, bp);
			}

			xz8 = new ImagePlus("XZ 8-bit", xz_stack);
			xz8.setLut(lut);
			final Calibration xzCal = xy8.getCalibration().copy();
			xzCal.pixelHeight = xy8.getCalibration().pixelDepth;
			xzCal.pixelDepth = xy8.getCalibration().pixelHeight;
			xz8.setCalibration(xzCal);
		}

		return new ImagePlus[] { xy8, xz8, zy8 };

	}

	private void updateLut() {
		final LUT[] luts = xy.getLuts(); // never null
		if (luts.length > 0) lut = luts[channel - 1];
	}

	/**
	 * Overlays a semi-transparent MIP (8-bit scaled) of the data being traced
	 * over the tracing canvas(es). Does nothing if image is 2D. Note that with
	 * multidimensional images, only the C,T position being traced is projected.
	 *
	 * @param opacity (alpha), in the range 0.0-1.0, where 0.0 is none (fully
	 *          transparent) and 1.0 is fully opaque. Setting opacity to zero
	 *          clears previous MIPs.
	 */
	public void showMIPOverlays(final double opacity) {
		if (is2D()) return;

		if (opacity == 0d) {
			removeMIPOverlayAllPanes();
			this.unzoomAllPanes();
			return;
		}

		final ImagePlus[] paneImps = new ImagePlus[] { xy, xz, zy };
		final ImagePlus[] paneMips = getLoadedDataGray8Panes();

		// Create a MIP Z-projection of the active channel
		for (int i = 0; i < paneImps.length; i++) {
			final ImagePlus paneImp = paneImps[i];
			final ImagePlus mipImp = paneMips[i];
			if (paneImp == null || mipImp == null || paneImp.getNSlices() == 1)
				continue;

			Overlay existingOverlay = paneImp.getOverlay();
			if (existingOverlay == null) existingOverlay = new Overlay();
			final ImagePlus overlay = ZProjector.run(mipImp, "max");

			// (This logic is taken from OverlayCommands.)
			final ImageRoi roi = new ImageRoi(0, 0, overlay.getProcessor());
			roi.setName(MIP_OVERLAY_IDENTIFIER);
			roi.setOpacity(opacity);
			existingOverlay.add(roi);
			paneImp.setOverlay(existingOverlay);
			paneImp.setHideOverlay(false);
		}
	}

	protected void discreteMsg(final String msg) { /* HTML format */
		new GuiUtils(getActiveCanvas()).tempMsg(msg);
	}

	protected boolean getConfirmation(final String msg, final String title) {
		return new GuiUtils(getActiveWindow()).getConfirmation(msg, title);
	}

	protected void toggleSnapCursor() {
		enableSnapCursor(!snapCursor);
	}

	/**
	 * Enables SNT's XYZ snap cursor feature. Does nothing if no image data is
	 * available
	 *
	 * @param enable whether cursor snapping should be enabled
	 */
	public synchronized void enableSnapCursor(final boolean enable) {
		final boolean validImage = accessToValidImageData();
		snapCursor = enable && validImage;
		if (isUIready()) {
			if (enable && !validImage) ui.noValidImageDataError();
			ui.useSnapWindow.setSelected(snapCursor);
			ui.snapWindowXYsizeSpinner.setEnabled(snapCursor);
			ui.snapWindowZsizeSpinner.setEnabled(snapCursor && !is2D());
		}
	}

	public void enableAutoActivation(final boolean enable) {
		autoCanvasActivation = enable;
	}

	// TODO: Use prefsService
	private boolean manualOverride = false;

	/**
	 * Enables (or disables) the A* search algorithm (enabled by default)
	 *
	 * @param enable true to enable A* search, false otherwise
	 */
	public void enableAstar(final boolean enable) {
		manualOverride = !enable;
		if (ui != null) ui.enableAStarGUI(enable);
	}

	/**
	 * Checks if A* search is enabled
	 *
	 * @return true, if A* search is enabled, otherwise false
	 */
	public boolean isAstarEnabled() {
		return !manualOverride;
	}

	/**
	 * @return true if the image currently loaded does not have a depth (Z)
	 *         dimension
	 */
	public boolean is2D() {
		return singleSlice;
	}

	protected boolean drawDiametersXY = Prefs.get(
		"tracing.Simple_Neurite_Tracer.drawDiametersXY", "false").equals("true");

	public void setDrawDiametersXY(final boolean draw) {
		drawDiametersXY = draw;
		repaintAllPanes();
	}

	public boolean getDrawDiametersXY() {
		return drawDiametersXY;
	}

	@Override
	public void closeAndResetAllPanes() {
		// Dispose xz/zy images unless the user stored some annotations (ROIs)
		// on the image overlay or modified them somehow. In that case, restore
		// them to the user
		removeMIPOverlayAllPanes();
		if (!single_pane) {
			final ImagePlus[] impPanes = { xz, zy };
			final StackWindow[] winPanes = { xz_window, zy_window };
			for (int i = 0; i < impPanes.length; ++i) {
				if (impPanes[i] == null) continue;
				final Overlay overlay = impPanes[i].getOverlay();
				if (!impPanes[i].changes && (overlay == null || impPanes[i].getOverlay()
					.size() == 0)) impPanes[i].close();
				else {
					winPanes[i] = new StackWindow(impPanes[i]);
					winPanes[i].getCanvas().add(ij.Menus.getPopupMenu());
					impPanes[i].setOverlay(overlay);
				}
			}
		}
		// Restore main view
		final Overlay overlay = (xy == null) ? null : xy.getOverlay();
		if (overlay == null && !accessToValidImageData()) {
			xy.close();
			return;
		}
		if (original_xy_canvas != null && xy != null && xy.getImage() != null) {
			xy_window = new StackWindow(xy, original_xy_canvas);
			xy.setOverlay(overlay);
			xy_window.getCanvas().add(ij.Menus.getPopupMenu());
		}

	}

	public Context getContext() {
		return context;
	}

	/**
	 * Gets the main UI.
	 *
	 * @return the main dialog of SNT's UI
	 */
	public SNTUI getUI() {
		return ui;
	}

	/* (non-Javadoc)
	 * @see tracing.hyperpanes.MultiDThreePanes#showStatus(int, int, java.lang.String)
	 */
	@Override
	public void showStatus(final int progress, final int maximum,
		final String status)
	{
		statusService.showStatus(progress, maximum, status);
		if (isUIready()) getUI().showStatus(status, true);
	}

}
