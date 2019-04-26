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

package tracing.viewer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.jzy3d.bridge.awt.FrameAWT;
import org.jzy3d.chart.AWTChart;
import org.jzy3d.chart.Chart;
import org.jzy3d.chart.controllers.ControllerType;
import org.jzy3d.chart.controllers.camera.AbstractCameraController;
import org.jzy3d.chart.controllers.mouse.AWTMouseUtilities;
import org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController;
import org.jzy3d.chart.factories.AWTChartComponentFactory;
import org.jzy3d.chart.factories.IChartComponentFactory;
import org.jzy3d.chart.factories.IFrame;
import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;
import org.jzy3d.colors.ISingleColorable;
import org.jzy3d.maths.BoundingBox3d;
import org.jzy3d.maths.Coord2d;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.maths.Rectangle;
import org.jzy3d.plot2d.primitive.AWTColorbarImageGenerator;
import org.jzy3d.plot3d.builder.Builder;
import org.jzy3d.plot3d.primitives.AbstractDrawable;
import org.jzy3d.plot3d.primitives.AbstractWireframeable;
import org.jzy3d.plot3d.primitives.LineStrip;
import org.jzy3d.plot3d.primitives.Point;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.primitives.Sphere;
import org.jzy3d.plot3d.primitives.Tube;
import org.jzy3d.plot3d.primitives.axes.layout.providers.ITickProvider;
import org.jzy3d.plot3d.primitives.axes.layout.providers.RegularTickProvider;
import org.jzy3d.plot3d.primitives.axes.layout.providers.SmartTickProvider;
import org.jzy3d.plot3d.primitives.axes.layout.renderers.FixedDecimalTickRenderer;
import org.jzy3d.plot3d.primitives.axes.layout.renderers.ITickRenderer;
import org.jzy3d.plot3d.primitives.axes.layout.renderers.ScientificNotationTickRenderer;
import org.jzy3d.plot3d.primitives.vbo.drawable.DrawableVBO;
import org.jzy3d.plot3d.rendering.canvas.ICanvas;
import org.jzy3d.plot3d.rendering.canvas.IScreenCanvas;
import org.jzy3d.plot3d.rendering.canvas.Quality;
import org.jzy3d.plot3d.rendering.legends.colorbars.AWTColorbarLegend;
import org.jzy3d.plot3d.rendering.scene.Scene;
import org.jzy3d.plot3d.rendering.view.AWTView;
import org.jzy3d.plot3d.rendering.view.View;
import org.jzy3d.plot3d.rendering.view.ViewportMode;
import org.jzy3d.plot3d.rendering.view.annotation.CameraEyeOverlayAnnotation;
import org.jzy3d.plot3d.rendering.view.modes.CameraMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewBoundMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewPositionMode;
import org.jzy3d.plot3d.transform.Transform;
import org.jzy3d.plot3d.transform.Translate;
import org.jzy3d.plot3d.transform.squarifier.XYSquarifier;
import org.jzy3d.plot3d.transform.squarifier.XZSquarifier;
import org.jzy3d.plot3d.transform.squarifier.ZYSquarifier;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.table.DefaultGenericTable;
import org.scijava.ui.awt.AWTWindows;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import org.scijava.util.FileUtils;

import com.jidesoft.swing.CheckBoxList;
import com.jidesoft.swing.CheckBoxTree;
import com.jidesoft.swing.ListSearchable;
import com.jidesoft.swing.SearchableBar;
import com.jidesoft.swing.TreeSearchable;
import com.jogamp.opengl.FPSCounter;
import com.jogamp.opengl.GLAnimatorControl;
import com.jogamp.opengl.GLException;

import ij.gui.HTMLDialog;
import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.SNT;
import tracing.SNTService;
import tracing.Tree;
import tracing.analysis.MultiTreeColorMapper;
import tracing.analysis.TreeAnalyzer;
import tracing.analysis.TreeColorMapper;
import tracing.analysis.TreeStatistics;
import tracing.annotation.AllenCompartment;
import tracing.annotation.AllenUtils;
import tracing.gui.GuiUtils;
import tracing.gui.IconFactory;
import tracing.gui.IconFactory.GLYPH;
import tracing.gui.SNTSearchableBar;
import tracing.gui.cmds.ColorMapReconstructionCmd;
import tracing.gui.cmds.DistributionCmd;
import tracing.gui.cmds.LoadObjCmd;
import tracing.gui.cmds.LoadReconstructionCmd;
import tracing.gui.cmds.MLImporterCmd;
import tracing.gui.cmds.RecViewerPrefsCmd;
import tracing.gui.cmds.RemoteSWCImporterCmd;
import tracing.gui.cmds.TranslateReconstructionsCmd;
import tracing.io.FlyCircuitLoader;
import tracing.io.NeuroMorphoLoader;
import tracing.plugin.ShollTracingsCmd;
import tracing.plugin.StrahlerCmd;
import tracing.util.PointInImage;
import tracing.util.SNTColor;
import tracing.util.SNTPoint;
import tracing.viewer.OBJMesh.RemountableDrawableVBO;

/**
 * Implements SNT's Reconstruction Viewer. Relies heavily on the
 * {@code org.jzy3d} package.
 *
 * @author Tiago Ferreira
 */
public class Viewer3D {

	/**
	 * Presets of a scene's view point.
	 */
	public enum ViewMode {
			/**
			 * No enforcement of view point: let the user freely turn around the
			 * scene.
			 */
			DEFAULT("Default"), //
			/**
			 * Enforce a lateral view point of the scene.
			 */
			SIDE("Side Constrained"), //
			/**
			 * Enforce a top view point of the scene with disabled rotation.
			 */
			TOP("Top Constrained"),
			/**
			 * Enforce an 'overview (two-point perspective) view point of the scene.
			 */
			PERSPECTIVE("Perspective");

		private String description;

		private ViewMode next() {
			switch (this) {
				case DEFAULT:
					return TOP;
				case TOP:
					return SIDE;
				case SIDE:
					return PERSPECTIVE;
				default:
					return DEFAULT;
			}
		}

		ViewMode(final String description) {
			this.description = description;
		}

	}

	private static final Map<String, String> TEMPLATE_MESHES;
	private final static String ALLEN_MESH_LABEL = "Whole Brain";
	private final static String JFRC2_MESH_LABEL = "JFRC2 Template";
	private final static String JFRC3_MESH_LABEL = "JFRC3 Template";
	private final static String FCWB_MESH_LABEL = "FCWB Template";
	static {
		TEMPLATE_MESHES = new HashMap<>();
		TEMPLATE_MESHES.put(ALLEN_MESH_LABEL, "MouseBrainAllen.obj");
		TEMPLATE_MESHES.put(JFRC2_MESH_LABEL, "JFRCtemplate2010.obj");
		TEMPLATE_MESHES.put(JFRC3_MESH_LABEL, "JFRCtemplate2013.obj");
		TEMPLATE_MESHES.put(FCWB_MESH_LABEL, "FCWB.obj");
	}
	private final static String PATH_MANAGER_TREE_LABEL = "Path Manager Contents";
	private final static float DEF_NODE_RADIUS = 3f;
	private static final Color DEF_COLOR = new Color(1f, 1f, 1f, 0.05f);
	private static final Color INVERTED_DEF_COLOR = new Color(0f, 0f, 0f, 0.05f);

	/* Maps for plotted objects */
	private final Map<String, ShapeTree> plottedTrees;
	private final Map<String, RemountableDrawableVBO> plottedObjs;

	/* Settings */
	private Color defColor;
	private float defThickness = DEF_NODE_RADIUS;
	private final Prefs prefs;

	/* Color Bar */
	private ColorLegend cBar;

	/* Manager */
	private CheckBoxList managerList;
	private DefaultListModel<Object> managerModel;

	private Chart chart;
	private View view;
	private ViewerFrame frame;
	private GuiUtils gUtils;
	private KeyController keyController;
	private MouseController mouseController;
	private boolean viewUpdatesEnabled = true;
	private final UUID uuid;
	private ViewMode currentView;

	@Parameter
	private Context context;

	@Parameter
	private CommandService cmdService;

	@Parameter
	private DisplayService displayService;

	@Parameter
	private SNTService sntService;

	@Parameter
	private PrefService prefService;

	/**
	 * Instantiates Viewer3D without the 'Controls' dialog ('kiosk mode'). Such
	 * a viewer is more suitable for large datasets and allows for {@link Tree}s to
	 * be added concurrently.
	 */
	public Viewer3D() {
		plottedTrees = new TreeMap<>();
		plottedObjs = new TreeMap<>();
		initView();
		uuid = UUID.randomUUID();
		prefs = new Prefs(this);
		prefs.setPreferences();
	}

	/**
	 * Instantiates an interactive Viewer3D with GUI Controls to import, manage
	 * and customize the Viewer's scene.
	 *
	 * @param context the SciJava application context providing the services
	 *          required by the class
	 */
	public Viewer3D(final Context context) {
		this();
		GuiUtils.setSystemLookAndFeel();
		initManagerList();
		context.inject(this);
		prefs.setPreferences();
	}

	/**
	 * Sets whether the scene should update (refresh) every time a new
	 * reconstruction (or mesh) is added/removed from the scene. Should be set to
	 * false when performing bulk operations;
	 *
	 * @param enabled Whether scene updates should be enabled
	 */
	public void setSceneUpdatesEnabled(final boolean enabled) {
		viewUpdatesEnabled = enabled;
	}

	private boolean chartExists() {
		return chart != null && chart.getCanvas() != null;
	}

	/* returns true if chart was initialized */
	private boolean initView() {
		if (chartExists()) return false;
		chart = new AChart(Quality.Nicest); // There does not seem to be a swing
																					// implementation of
		// ICameraMouseController so we are stuck with AWT
		chart.black();
		view = chart.getView();
		view.setBoundMode(ViewBoundMode.AUTO_FIT);
		keyController = new KeyController(chart);
		mouseController = new MouseController(chart);
		chart.getCanvas().addKeyController(keyController);
		chart.getCanvas().addMouseController(mouseController);
		chart.setAxeDisplayed(false);
		squarify("none", false);
		currentView = ViewMode.DEFAULT;
		gUtils = new GuiUtils((Component) chart.getCanvas());
		return true;
	}

	private void squarify(final String axes, final boolean enable) {
		final String parsedAxes = (enable) ? axes.toLowerCase() : "none";
		switch (parsedAxes) {
			case "xy":
				view.setSquarifier(new XYSquarifier());
				view.setSquared(true);
				return;
			case "zy":
				view.setSquarifier(new ZYSquarifier());
				view.setSquared(true);
				return;
			case "xz":
				view.setSquarifier(new XZSquarifier());
				view.setSquared(true);
				return;
			default:
				view.setSquarifier(null);
				view.setSquared(false);
				return;
		}
	}

	private void rebuild() {
		SNT.log("Rebuilding scene...");
		try {
			// remember settings so that they can be restored
			final boolean lighModeOn = !isDarkModeOn();
			final float currentZoomStep = keyController.zoomStep;
			final double currentRotationStep = keyController.rotationStep;
			final float currentPanStep = mouseController.panStep;
			chart.stopAnimator();
			chart.dispose();
			chart = null;
			initView();
			keyController.zoomStep = currentZoomStep;
			keyController.rotationStep = currentRotationStep;
			mouseController.panStep = currentPanStep;
			if (lighModeOn) keyController.toggleDarkMode();
			addAllObjects();
			updateView();
			//if (managerList != null) managerList.selectAll();
		}
		catch (final GLException exc) {
			SNT.error("Rebuild Error", exc);
		}
		if (frame != null) frame.replaceCurrentChart(chart);
		updateView();
	}

	/**
	 * Checks if all drawables in the 3D scene are being rendered properly,
	 * rebuilding the entire scene if not. Useful to "hard-reset" the viewer, e.g.,
	 * to ensure all meshes are redrawn.
	 *
	 * @see #updateView()
	 */
	public void validate() {
		if (!sceneIsOK()) rebuild();
	}

	/**
	 * Enables/disables debug mode
	 *
	 * @param enable true to enable debug mode, otherwise false
	 */
	public void setEnableDebugMode(final boolean enable) {
		if (frame != null && frame.managerPanel != null) {
			frame.managerPanel.debugCheckBox.setSelected(enable);
		}
		SNT.setDebugMode(enable);
	}

	/**
	 * Rotates the scene.
	 *
	 * @param degrees the angle, in degrees
	 * @throws IllegalArgumentException if current view mode does not allow
	 *           rotations
	 */
	public void rotate(final float degrees) throws IllegalArgumentException {
		if (currentView == ViewMode.TOP) {
			throw new IllegalArgumentException("Rotations not allowed under " +
				ViewMode.TOP.description);
		}
		mouseController.rotate(new Coord2d(-Math.toRadians(degrees), 0),
			viewUpdatesEnabled);
	}

	/**
	 * Records an animated rotation of the scene as a sequence of images.
	 *
	 * @param angle the rotation angle (e.g., 360 for a full rotation)
	 * @param frames the number of frames in the animated sequence
	 * @param destinationDirectory the directory where the image sequence will be
	 *          stored.
	 * @throws IllegalArgumentException if no view exists, or current view is
	 *           constrained and does not allow 360 degrees rotation
	 * @throws SecurityException if it was not possible to save files to
	 *           {@code destinationDirectory}
	 */
	public void recordRotation(final float angle, final int frames, final File destinationDirectory) throws IllegalArgumentException,
		SecurityException
	{
		if (!chartExists()) {
			throw new IllegalArgumentException("Viewer is not visible");
		}
		if (chart.getViewMode() == ViewPositionMode.TOP) {
			throw new IllegalArgumentException(
				"Current constrained view does not allow scene to be rotated.");
		}
		mouseController.stopThreadController();
		mouseController.recordRotation(angle, frames, destinationDirectory);
	}

	private boolean isDarkModeOn() {
		return view.getBackgroundColor() == Color.BLACK;
	}

	private void addAllObjects() {
		if (cBar != null) {
			cBar.updateColors();
			chart.add(cBar.get(), false);
		}
		plottedObjs.forEach((k, drawableVBO) -> {
			drawableVBO.unmount();
			chart.add(drawableVBO, false);
		});
		plottedTrees.values().forEach(shapeTree -> chart.add(shapeTree.get(),
			false));
	}

	private void initManagerList() {
		managerModel = new DefaultListModel<>();
		managerList = new CheckBoxList(managerModel);
		managerModel.addElement(CheckBoxList.ALL_ENTRY);
		managerList.getCheckBoxListSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				final List<String> selectedKeys = getLabelsCheckedInManager();
				plottedTrees.forEach((k1, shapeTree) -> {
					shapeTree.setDisplayed(selectedKeys.contains(k1));
				});
				plottedObjs.forEach((k2, drawableVBO) -> {
					drawableVBO.setDisplayed(selectedKeys.contains(k2));
				});
				// view.shoot();
			}
		});
	}

	private Color fromAWTColor(final java.awt.Color color) {
		return (color == null) ? getDefColor() : new Color(color.getRed(), color
			.getGreen(), color.getBlue(), color.getAlpha());
	}

	private Color fromColorRGB(final ColorRGB color) {
		return (color == null) ? getDefColor() : new Color(color.getRed(), color
			.getGreen(), color.getBlue(), color.getAlpha());
	}

	private String makeUniqueKey(final Map<String, ?> map, final String key) {
		for (int i = 2; i <= 100; i++) {
			final String candidate = key + " (" + i + ")";
			if (!map.containsKey(candidate)) return candidate;
		}
		return key + " (" + UUID.randomUUID() + ")";
	}

	private String getUniqueLabel(final Map<String, ?> map,
		final String fallbackPrefix, final String candidate)
	{
		final String label = (candidate == null || candidate.trim().isEmpty())
			? fallbackPrefix : candidate;
		return (map.containsKey(label)) ? makeUniqueKey(map, label) : label;
	}

	/**
	 * Adds a tree to this viewer. Note that calling {@link #updateView()} may be
	 * required to ensure that the current View's bounding box includes the added
	 * Tree.
	 *
	 * @param tree the {@link Tree} to be added. The Tree's label will be used as
	 *          identifier. It is expected to be unique when rendering multiple
	 *          Trees, if not (or no label exists) a unique label will be
	 *          generated.
	 * @see Tree#getLabel()
	 * @see #remove(String)
	 * @see #updateView()
	 */
	public void add(final Tree tree) {
		final String label = getUniqueLabel(plottedTrees, "Tree ", tree.getLabel());
		final ShapeTree shapeTree = new ShapeTree(tree);
		plottedTrees.put(label, shapeTree);
		addItemToManager(label);
		chart.add(shapeTree.get(), viewUpdatesEnabled);
	}

	/**
	 * Computes a Delaunay surface from a collection of points and adds it to the
	 * scene using default options.
	 * 
	 * @param points the collection of points defining the triangulated surface
	 * @see #addSurface(Collection, ColorRGB, double)
	 */
	public void addSurface(final Collection<? extends SNTPoint> points) {
		addSurface(points, null, 50);
	}

	/**
	 * Computes a Delaunay surface from a collection of points and adds it to the
	 * scene.
	 *
	 * @param points the collection of points defining the triangulated surface
	 * @param color the color to render the surface
	 * @param transparencyPercent the color transparency (in percentage)
	 * @see #addSurface(Collection)
	 */
	public void addSurface(final Collection<? extends SNTPoint> points,
		final ColorRGB color, final double transparencyPercent)
	{
		Color c = (color == null) ? Color.WHITE: fromColorRGB(color);
		c = c.alphaSelf((float) (1 -transparencyPercent / 100));
		addSurface(points, c);
	}

	private void addSurface(final Collection<? extends SNTPoint> points,
		final Color surfaceColor)
	{
		final List<Coord3d> coordinates = new ArrayList<>();
		for (final SNTPoint point : points) {
			coordinates.add(new Coord3d(point.getX(), point.getY(), point.getZ()));
		}
		final Shape surface = Builder.buildDelaunay(coordinates);
		surface.setFaceDisplayed(true);
		surface.setWireframeDisplayed(true);
		surface.setColor(surfaceColor);
		surface.setWireframeColor(Utils.contrastColor(surfaceColor));
		chart.add(surface, viewUpdatesEnabled);
	}

	private void addItemToManager(final String label) {
		if (managerList == null) return;
		final int[] indices = managerList.getCheckBoxListSelectedIndices();
		final int index = managerModel.size() - 1;
		managerModel.insertElementAt(label, index);
		// managerList.ensureIndexIsVisible(index);
		managerList.addCheckBoxListSelectedIndex(index);
		for (final int i : indices)
			managerList.addCheckBoxListSelectedIndex(i);
	}

	private boolean deleteItemFromManager(final String label) {
		return managerModel != null && managerModel.removeElement(label);
	}

	/**
	 * Updates the scene bounds to ensure all visible objects are displayed.
	 * 
	 * @see #rebuild()
	 */
	public void updateView() {
		if (view != null) {
			view.shoot(); // !? without forceRepaint() dimensions are not updated
			fitToVisibleObjects(false);
		}
	}

	/**
	 * Adds a color bar legend (LUT ramp) using default settings.
	 *
	 * @param colorTable the color table
	 * @param min the minimum value in the color table
	 * @param max the maximum value in the color table
	 */
	public void addColorBarLegend(final ColorTable colorTable, final double min,
		final double max)
	{
		cBar = new ColorLegend(new ColorTableMapper(colorTable, min, max));
		chart.add(cBar.get(), viewUpdatesEnabled);
	}

	/**
	 * Updates the existing color bar legend to new properties. Does nothing if no
	 * legend exists.
	 *
	 * @param min the minimum value in the color table
	 * @param max the maximum value in the color table
	 */
	public void updateColorBarLegend(final double min, final double max)
	{
		if (cBar != null) cBar.update(min, max);
	}

	/**
	 * Adds a color bar legend (LUT ramp).
	 *
	 * @param colorTable the color table
	 * @param min the minimum value in the color table
	 * @param max the maximum value in the color table
	 * @param font the font the legend font.
	 * @param steps the number of ticks in the legend. Tick placement is computed
	 *          automatically if negative.
	 * @param precision the number of decimal places of tick labels. scientific
	 *          notation is used if negative.
	 */
	public void addColorBarLegend(final ColorTable colorTable, final double min,
		final double max, final Font font, final int steps, final int precision)
	{
		cBar = new ColorLegend(new ColorTableMapper(colorTable, min, max), font,
			steps, precision);
		chart.add(cBar.get(), viewUpdatesEnabled);
	}

	/**
	 * Displays the Viewer and returns a reference to its frame. If the frame has
	 * been made displayable, this will simply make the frame visible. Typically,
	 * it should only be called once all objects have been added to the scene. If
	 * this is an interactive viewer, the 'Controls' dialog is also displayed.
	 *
	 * @return the frame containing the viewer.
	 */
	public Frame show() {
		return show(-1, -1);
	}

	/**
	 * Displays the viewer under specified dimensions. Useful when generating
	 * scene animations programmatically.
	 * 
	 * @param width the width of the frame
	 * @param height the height of the frame
	 * @return the frame containing the viewer.
	 * @see #show()
	 */
	public Frame show(final int width, final int height) {
		final boolean viewInitialized = initView();
		if (!viewInitialized && frame != null) {
			updateView();
			frame.setVisible(true);
			if (width > 0 || height > 0) {
				frame.setSize(width, height);
			}
			return frame;
		}
		else if (viewInitialized) {
			plottedTrees.forEach((k, shapeTree) -> {
				chart.add(shapeTree.get(), viewUpdatesEnabled);
			});
			plottedObjs.forEach((k, drawableVBO) -> {
				chart.add(drawableVBO, viewUpdatesEnabled);
			});
		}
		frame = (width < 0 || height < 0) ? 
			new ViewerFrame(chart, managerList != null)
			: 	new ViewerFrame(chart, width, height, managerList != null);
		displayMsg("Press 'H' or 'F1' for help", 3000);
		return frame;
	}

	private void displayMsg(final String msg) {
		displayMsg(msg, 2500);
	}

	private void displayMsg(final String msg, final int msecs) {
		if (gUtils != null && chartExists()) {
			gUtils.setTmpMsgTimeOut(msecs);
			gUtils.tempMsg(msg);
		}
		else {
			System.out.println(msg);
		}
	}

	/**
	 * Returns the Collection of Trees in this viewer.
	 *
	 * @return the rendered Trees (keys being the Tree identifier as per
	 *         {@link #add(Tree)})
	 */
	private Map<String, Shape> getTrees() {
		final Map<String, Shape> map = new HashMap<>();
		plottedTrees.forEach((k, shapeTree) -> {
			map.put(k, shapeTree.get());
		});
		return map;
	}

	/**
	 * Returns the Collection of OBJ meshes imported into this viewer.
	 *
	 * @return the rendered Meshes (keys being the filename of the imported OBJ
	 *         file as per {@link #loadOBJ(String, ColorRGB, double)}
	 */
	private Map<String, OBJMesh> getOBJs() {
		final Map<String, OBJMesh> newMap = new LinkedHashMap<>();
		plottedObjs.forEach((k, drawable) -> {
			newMap.put(k, drawable.objMesh);
		});
		return newMap;
	}

	/**
	 * Removes the specified Tree.
	 *
	 * @param tree the tree to be removed.
	 * @return true, if tree was successfully removed.
	 * @see #add(Tree)
	 */
	public boolean remove(final Tree tree) {
		return remove(getLabel(tree));
	}

	/**
	 * Removes the specified Tree.
	 *
	 * @param treeLabel the key defining the tree to be removed.
	 * @return true, if tree was successfully removed.
	 * @see #add(Tree)
	 */
	public boolean remove(final String treeLabel) {
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree == null) return false;
		boolean removed = plottedTrees.remove(treeLabel) != null;
		if (chart != null) {
			removed = removed && chart.getScene().getGraph().remove(shapeTree.get(),
				viewUpdatesEnabled);
			if (removed) deleteItemFromManager(treeLabel);
		}
		return removed;
	}

	/**
	 * Removes the specified OBJ mesh.
	 *
	 * @param mesh the OBJ mesh to be removed.
	 * @return true, if mesh was successfully removed.
	 * @see #loadOBJ(String, ColorRGB, double)
	 */
	public boolean removeOBJ(final OBJMesh mesh) {
		final String meshLabel = getLabel(mesh);
		return removeDrawable(plottedObjs, meshLabel);
	}

	/**
	 * Removes the specified OBJ mesh.
	 *
	 * @param meshLabel the key defining the OBJ mesh to be removed.
	 * @return true, if mesh was successfully removed.
	 * @see #loadOBJ(String, ColorRGB, double)
	 */
	public boolean removeOBJ(final String meshLabel) {
		return removeDrawable(plottedObjs, meshLabel);
	}

	/**
	 * Removes all loaded OBJ meshes from current viewer
	 */
	public void removeAllOBJs() {
		final Iterator<Entry<String, RemountableDrawableVBO>> it = plottedObjs
			.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, RemountableDrawableVBO> entry = it.next();
			chart.getScene().getGraph().remove(entry.getValue(), false);
			deleteItemFromManager(entry.getKey());
			if (frame.allenNavigator != null)
				frame.allenNavigator.meshRemoved(entry.getKey());
			it.remove();
		}
		if (viewUpdatesEnabled) chart.render();
	}

	/**
	 * Removes all the Trees from current viewer
	 */
	public void removeAll() {
		final Iterator<Entry<String, ShapeTree>> it = plottedTrees.entrySet()
			.iterator();
		while (it.hasNext()) {
			final Map.Entry<String, ShapeTree> entry = it.next();
			chart.getScene().getGraph().remove(entry.getValue().get(), false);
			deleteItemFromManager(entry.getKey());
			it.remove();
		}
		if (viewUpdatesEnabled) chart.render();
	}

	@SuppressWarnings("unchecked")
	private List<String> getLabelsCheckedInManager() {
		final Object[] values = managerList.getCheckBoxListSelectedValues();
		return (List<String>) (List<?>) Arrays.asList(values);
	}

	private <T extends AbstractDrawable> boolean allDrawablesRendered(
		final BoundingBox3d viewBounds, final Map<String, T> map,
		final List<String> selectedKeys)
	{
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, T> entry = it.next();
			final T drawable = entry.getValue();
			final BoundingBox3d bounds = drawable.getBounds();
			if (bounds == null || !viewBounds.contains(bounds)) return false;
			if ((selectedKeys.contains(entry.getKey()) && !drawable.isDisplayed())) {
				drawable.setDisplayed(true);
				if (!drawable.isDisplayed()) return false;
			}
		}
		return true;
	}

	private synchronized <T extends AbstractDrawable> boolean removeDrawable(
		final Map<String, T> map, final String label)
	{
		final T drawable = map.get(label);
		if (drawable == null) return false;
		boolean removed = map.remove(label) != null;
		if (chart != null) {
			removed = removed && chart.getScene().getGraph().remove(drawable,
				viewUpdatesEnabled);
			if (removed) {
				deleteItemFromManager(label);
				if (frame.allenNavigator != null)
					frame.allenNavigator.meshRemoved(label);
			}
		}
		return removed;
	}

	/**
	 * (Re)loads the current list of Paths in the Path Manager list.
	 *
	 * @return true, if synchronization was apparently successful, false otherwise
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public boolean syncPathManagerList() throws UnsupportedOperationException {
		if (SNT.getPluginInstance() == null) throw new IllegalArgumentException(
			"SNT is not running.");
		final Tree tree = new Tree(SNT.getPluginInstance().getPathAndFillManager()
			.getPathsFiltered());
		if (plottedTrees.containsKey(PATH_MANAGER_TREE_LABEL)) {
			chart.getScene().getGraph().remove(plottedTrees.get(
				PATH_MANAGER_TREE_LABEL).get());
			final ShapeTree newShapeTree = new ShapeTree(tree);
			plottedTrees.replace(PATH_MANAGER_TREE_LABEL, newShapeTree);
			chart.add(newShapeTree.get(), viewUpdatesEnabled);
		}
		else {
			tree.setLabel(PATH_MANAGER_TREE_LABEL);
			add(tree);
		}
		updateView();
		return plottedTrees.get(PATH_MANAGER_TREE_LABEL).get().isDisplayed();
	}

	private boolean isValid(final AbstractDrawable drawable) {
		return drawable.getBounds() != null && drawable.getBounds().getRange()
			.distanceSq(new Coord3d(0f, 0f, 0f)) > 0f;
	}

	private boolean sceneIsOK() {
		try {
			updateView();
		}
		catch (final GLException ignored) {
			SNT.log("Upate view failed...");
			return false;
		}
		// now check that everything is visible
		if (managerList == null) return true;
		final List<String> selectedKeys = getLabelsCheckedInManager();
		final BoundingBox3d viewBounds = chart.view().getBounds();
		return allDrawablesRendered(viewBounds, plottedObjs, selectedKeys) &&
			allDrawablesRendered(viewBounds, getTrees(), selectedKeys);
	}

	/** returns true if a drawable was removed */
	@SuppressWarnings("unused")
	private <T extends AbstractDrawable> boolean removeInvalid(
		final Map<String, T> map)
	{
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		final int initialSize = map.size();
		while (it.hasNext()) {
			final Entry<String, T> entry = it.next();
			if (!isValid(entry.getValue())) {
				if (chart.getScene().getGraph().remove(entry.getValue(), false))
					deleteItemFromManager(entry.getKey());
				it.remove();
			}
		}
		return initialSize > map.size();
	}

	/**
	 * Toggles the visibility of a rendered Tree or a loaded OBJ mesh.
	 *
	 * @param treeOrObjLabel the unique identifier of the Tree (as per
	 *          {@link #add(Tree)}), or the filename of the loaded OBJ
	 *          {@link #loadOBJ(String, ColorRGB, double)}
	 * @param visible whether the Object should be displayed
	 */
	public void setVisible(final String treeOrObjLabel, final boolean visible) {
		final ShapeTree treeShape = plottedTrees.get(treeOrObjLabel);
		if (treeShape != null) treeShape.get().setDisplayed(visible);
		final DrawableVBO obj = plottedObjs.get(treeOrObjLabel);
		if (obj != null) obj.setDisplayed(visible);
	}

	/**
	 * Runs {@link MultiTreeColorMapper} on the specified collection of
	 * {@link Tree}s.
	 *
	 * @param treeLabels the collection of Tree identifiers (as per
	 *          {@link #add(Tree)}) specifying the Trees to be color mapped
	 * @param measurement the mapping measurement e.g.,
	 *          {@link MultiTreeColorMapper#TOTAL_LENGTH}
	 *          {@link MultiTreeColorMapper#TOTAL_N_TIPS}, etc.
	 * @param colorTable the mapping color table (LUT), e.g.,
	 *          {@link ColorTables#ICE}), or any other known to LutService
	 * @return the double[] the limits (min and max) of the mapped values
	 */
	public double[] colorCode(final Collection<String> treeLabels,
		final String measurement, final ColorTable colorTable)
	{
		final List<ShapeTree> shapeTrees = new ArrayList<>();
		final List<Tree> trees = new ArrayList<>();
		treeLabels.forEach(label -> {
			final ShapeTree sTree = plottedTrees.get(label);
			if (sTree != null) {
				shapeTrees.add(sTree);
				trees.add(sTree.tree);
			}
		});
		final MultiTreeColorMapper mapper = new MultiTreeColorMapper(trees);
		mapper.map(measurement, colorTable);
		shapeTrees.forEach(st -> st.rebuildShape());
		return mapper.getMinMax();
	}

	/**
	 * Toggles the Viewer's animation.
	 *
	 * @param enabled if true animation starts. Stops if false
	 */
	public void setAnimationEnabled(final boolean enabled) {
		if (mouseController == null) return;
		if (enabled) mouseController.startThreadController();
		else mouseController.stopThreadController();
	}

	/**
	 * Sets a manual bounding box for the scene. The bounding box determines the
	 * zoom and framing of the scene. Current view point is logged to the Console
	 * when interacting with the Reconstruction Viewer in debug mode.
	 *
	 * @param xMin the X coordinate of the box origin
	 * @param xMax the X coordinate of the box origin opposite
	 * @param yMin the Y coordinate of the box origin
	 * @param yMax the Y coordinate of the box origin opposite
	 * @param zMin the Z coordinate of the box origin
	 * @param zMax the X coordinate of the box origin opposite
	 */
	public void setBounds(final float xMin, final float xMax, final float yMin, final float yMax,
		final float zMin, final float zMax)
	{
		final BoundingBox3d bBox = new BoundingBox3d(xMin, xMax, yMin, yMax, zMin,
			zMax);
		chart.view().setBoundManual(bBox);
		if (viewUpdatesEnabled) chart.view().shoot();
	}

	/**
	 * Runs {@link TreeColorMapper} on the specified {@link Tree}.
	 *
	 * @param treeLabel the identifier of the Tree (as per {@link #add(Tree)})to
	 *          be color mapped
	 * @param measurement the mapping measurement e.g.,
	 *          {@link TreeColorMapper#BRANCH_ORDER}
	 *          {@link TreeColorMapper#PATH_DISTANCE}, etc.
	 * @param colorTable the mapping color table (LUT), e.g.,
	 *          {@link ColorTables#ICE}), or any other known to LutService
	 * @return the double[] the limits (min and max) of the mapped values
	 */
	public double[] colorCode(final String treeLabel, final String measurement,
		final ColorTable colorTable)
	{
		final ShapeTree treeShape = plottedTrees.get(treeLabel);
		if (treeShape == null) return null;
		return treeShape.colorize(measurement, colorTable);
	}

	/**
	 * Renders the scene from a specified camera angle.
	 *
	 * @param viewMode the view mode, e.g., {@link ViewMode#DEFAULT},
	 *          {@link ViewMode#SIDE} , etc.
	 */
	public void setViewMode(final ViewMode viewMode) {
		if (!chartExists()) {
			throw new IllegalArgumentException("View was not initialized?");
		}
		((AChart) chart).setViewMode(viewMode);
	}

	/**
	 * Renders the scene from a specified camera angle using polar coordinates
	 * relative to the the center of the scene. Only X and Y dimensions are
	 * required, as the distance to center is automatically computed. Current
	 * view point is logged to the Console when interacting with the
	 * Reconstruction Viewer in debug mode.
	 * 
	 * @param r the radial coordinate
	 * @param t the angle coordinate (in radians)
	 */
	public void setViewPoint(final float r, final float t) {
		if (!chartExists()) {
			throw new IllegalArgumentException("View was not initialized?");
		}
		chart.getView().setViewPoint(new Coord3d(r, t, Float.NaN));
		if (viewUpdatesEnabled) chart.getView().shoot();
	}

	/**
	 * Adds an annotation label to the scene.
	 *
	 * @param label the annotation text
	 * @see #setFont(Font, float, ColorRGB)
	 * @see #setLabelLocation(float, float)
	 */
	public void setLabel(final String label) {
		((AChart)chart).overlayAnnotation.label = label;
	}

	/**
	 * Sets the location for annotation labels
	 *
	 * @param x the x position of the label
	 * @param y the y position of the label
	 */
	public void setLabelLocation(final float x, final float y) {
		((AChart)chart).overlayAnnotation.labelX = x;
		((AChart)chart).overlayAnnotation.labelY = y;
	}

	/**
	 * Sets the font for label annotations
	 *
	 * @param font the font label, e.g.,
	 *          {@code new Font(Font.SANS_SERIF, Font.ITALIC, 20)}
	 * @param angle the angle in degrees for rotated labels
	 * @param color the font color, e.g., {@code org.scijava.util.Colors.ORANGE}
	 */
	public void setFont(final Font font, final float angle, final ColorRGB color) {
		((AChart)chart).overlayAnnotation.setFont(font, angle);
		((AChart)chart).overlayAnnotation.setLabelColor(new java.awt.Color(color.getRed(), color
			.getGreen(), color.getBlue(), color.getAlpha()));
	}

	/**
	 * Saves a snapshot of current scene as a PNG image. Image is saved using an
	 * unique time stamp as a file name in the directory specified in the
	 * preferences dialog or through {@link #setSnapshotDir(String)}
	 *
	 * @return true, if successful
	 * @throws IllegalArgumentException if Viewer is not available
	 */
	public boolean saveSnapshot() throws IllegalArgumentException {
		if (!chartExists()) {
			throw new IllegalArgumentException("Viewer is not visible");
		}
		final String file = new SimpleDateFormat("'SNT 'yyyy-MM-dd HH-mm-ss'.png'")
			.format(new Date());
		try {
			final File f = new File(prefs.snapshotDir, file);
			SNT.log("Saving snapshot to " + f);
			if (SNT.isDebugMode() && frame != null) {
				SNT.log("Frame size: (" + frame.getWidth() + ", " + frame.getHeight() + ");");
				((AView)view).logViewPoint();
			}
			chart.screenshot(f);
		}
		catch (final IOException e) {
			SNT.error("IOException", e);
			return false;
		}
		return true;
	}

	/**
	 * Sets the directory for storing snapshots.
	 *
	 * @param path the absolute path to the new snapshot directory.
	 */
	public void setSnapshotDir(final String path) {
		prefs.snapshotDir = path;
	}

	/**
	 * Loads a Wavefront .OBJ file. Files should be loaded _before_ displaying the
	 * scene, otherwise, if the scene is already visible, {@link #validate()}
	 * should be called to ensure all meshes are visible.
	 *
	 * @param filePath the absolute file path (or URL) of the file to be imported.
	 *          The filename is used as unique identifier of the object (see
	 *          {@link #setVisible(String, boolean)})
	 * @param color the color to render the imported file
	 * @param transparencyPercent the color transparency (in percentage)
	 * @return the loaded OBJ mesh
	 * @throws IllegalArgumentException if filePath is invalid or file does not
	 *           contain a compilable mesh
	 */
	public OBJMesh loadOBJ(final String filePath, final ColorRGB color,
		final double transparencyPercent) throws IllegalArgumentException
	{
		final OBJMesh objMesh = new OBJMesh(filePath);
		objMesh.setColor(color, transparencyPercent);
		return loadOBJMesh(objMesh);
	}

	/**
	 * Loads a Wavefront .OBJ file. Should be called before_ displaying the scene,
	 * otherwise, if the scene is already visible, {@link #validate()} should be
	 * called to ensure all meshes are visible.
	 *
	 * @param objMesh the mesh to be loaded
	 * @return true, if successful
	 * @throws IllegalArgumentException if mesh could not be compiled
	 */
	public boolean loadMesh(final OBJMesh objMesh) throws IllegalArgumentException {
		return loadOBJMesh(objMesh) != null;
	}

	private OBJMesh loadOBJMesh(final OBJMesh objMesh) {
		chart.add(objMesh.drawable, false); // GLException if true
		final String label = getUniqueLabel(plottedObjs, "Mesh", objMesh.getLabel());
		plottedObjs.put(label, objMesh.drawable);
		addItemToManager(label);
		if (frame != null && frame.allenNavigator != null) {
			frame.allenNavigator.meshLoaded(label);
		}
		return objMesh;
	}

	/**
	 * Loads the surface meshes for the Allen Mouse Brain Atlas (CCF). It will
	 * simply make the mesh visible if has already been loaded.
	 *
	 * @return a reference to the loaded mesh
	 * @throws IllegalArgumentException if Viewer is not available
	 */
	public OBJMesh loadMouseRefBrain() throws IllegalArgumentException {
		return AllenUtils.getRootMesh((isDarkModeOn()?Colors.WHITE:Colors.BLACK));
	}

	/**
	 * Loads the surface mesh of a Drosophila template brain. It will simply make
	 * the mesh visible if has already been loaded. These meshes are detailed on
	 * the <a href=
	 * "https://www.rdocumentation.org/packages/nat.templatebrains/">nat.flybrains</a>
	 * documentation.
	 *
	 * @param templateBrain the template brain to be loaded (case-insensitive).
	 *          Either "JFRC2" (AKA JFRC2010), "JFRC3" (AKA JFRC2013), or "FCWB"
	 *          (FlyCircuit Whole Brain Template)
	 * @return a reference to the loaded mesh
	 * @throws IllegalArgumentException if templateBrain is not recognized, or
	 *           Viewer is not available
	 */
	public OBJMesh loadDrosoRefBrain(final String templateBrain)
		throws IllegalArgumentException
	{
		final String inputType = (templateBrain == null) ? null : templateBrain
			.toLowerCase();
		switch (inputType) {
			case "jfrc2":
			case "jfrc2010":
			case "jfrctemplate2010":
				return loadRefBrain(JFRC2_MESH_LABEL);
			case "jfrc3":
			case "jfrc2013":
			case "jfrctemplate2013":
				return loadRefBrain(JFRC3_MESH_LABEL);
			case "fcwb":
			case "flycircuit":
				return loadRefBrain(FCWB_MESH_LABEL);
			default:
				throw new IllegalArgumentException("Invalid argument");
		}
	}

	private OBJMesh loadRefBrain(final String label)
		throws IllegalArgumentException
	{
		if (plottedObjs.keySet().contains(label)) {
			setVisible(label, true);
			return plottedObjs.get(label).objMesh;
		}
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		final URL url = loader.getResource("meshes/" + TEMPLATE_MESHES.get(label));
		if (url == null) throw new IllegalArgumentException(label + " not found.");
		final OBJMesh objMesh = new OBJMesh(url);
		objMesh.setLabel(label);
		objMesh.drawable.setColor(getNonUserDefColor());
		return loadOBJMesh(objMesh);
	}

	private Color getNonUserDefColor() {
		return (isDarkModeOn()) ? DEF_COLOR : INVERTED_DEF_COLOR;
	}

	private Color getDefColor() {
		return (defColor == null) ? getNonUserDefColor() : defColor;
	}

//	/**
//	 * Returns this viewer's {@link View} holding {@link Scene}, {@link LightSet},
//	 * {@link ICanvas}, etc.
//	 *
//	 * @return this viewer's View, or null if it was disposed after {@link #show()}
//	 *         has been called
//	 */
//	public View getView() {
//		return (chart == null) ? null : view;
//	}

	/** ChartComponentFactory adopting {@link AView} */
	private class AChartComponentFactory extends AWTChartComponentFactory {

		@Override
		public View newView(final Scene scene, final ICanvas canvas,
			final Quality quality)
		{
			return new AView(getFactory(), scene, canvas, quality);
		}
	}

	/** AWTChart adopting {@link AView} */
	private class AChart extends AWTChart {

		private final Coord3d TOP_VIEW = new Coord3d(Math.PI / 2, 0.5, 3000);
		private final Coord3d PERSPECTIVE_VIEW = new Coord3d(Math.PI / 2, 0.5, 3000);

		private Coord3d previousViewPointPerspective;
		private OverlayAnnotation overlayAnnotation;

		public AChart(final Quality quality) {
			super(new AChartComponentFactory(), quality, DEFAULT_WINDOWING_TOOLKIT,
				org.jzy3d.chart.Settings.getInstance().getGLCapabilities());
			currentView = ViewMode.DEFAULT;
			addRenderer(overlayAnnotation = new OverlayAnnotation(getView()));
		}

		// see super.setViewMode(mode);
		public void setViewMode(final ViewMode view) {
			// Store current view mode and view point in memory
			if (currentView == ViewMode.DEFAULT) previousViewPointFree = getView()
				.getViewPoint();
			else if (currentView == ViewMode.TOP) previousViewPointTop = getView()
				.getViewPoint();
			else if (currentView == ViewMode.SIDE) previousViewPointProfile = getView()
				.getViewPoint();
			else if (currentView == ViewMode.PERSPECTIVE) previousViewPointPerspective =
				getView().getViewPoint();

			// Set new view mode and former view point
			getView().setViewPositionMode(null);
			if (view == ViewMode.DEFAULT) {
				getView().setViewPositionMode(ViewPositionMode.FREE);
				getView().setViewPoint(previousViewPointFree == null ? View.DEFAULT_VIEW
					.clone() : previousViewPointFree);
			}
			else if (view == ViewMode.TOP) {
				getView().setViewPositionMode(ViewPositionMode.TOP);
				getView().setViewPoint(previousViewPointTop == null ? TOP_VIEW.clone()
					: previousViewPointTop);
			}
			else if (view == ViewMode.SIDE) {
				getView().setViewPositionMode(ViewPositionMode.PROFILE);
				getView().setViewPoint(previousViewPointProfile == null
					? View.DEFAULT_VIEW.clone() : previousViewPointProfile);
			}
			else if (view == ViewMode.PERSPECTIVE) {
				getView().setViewPositionMode(ViewPositionMode.FREE);
				getView().setViewPoint(previousViewPointPerspective == null
					? PERSPECTIVE_VIEW.clone() : previousViewPointPerspective);
			}
			getView().shoot();
			currentView = view;
		}

	}


	/** AWTColorbarLegend with customizable font/ticks/decimals, etc. */
	private class ColorLegend extends AWTColorbarLegend {

		private final Shape shape;
		private final Font font;

		public ColorLegend(final ColorTableMapper mapper, final Font font,
			final int steps, final int precision)
		{
			super(new Shape(), chart);
			shape = (Shape) drawable;
			this.font = font;
			shape.setColorMapper(mapper);
			shape.setLegend(this);
			updateColors();
			provider = (steps < 0) ? new SmartTickProvider(5)
				: new RegularTickProvider(steps);
			renderer = (precision < 0) ? new ScientificNotationTickRenderer(-1 *
				precision) : new FixedDecimalTickRenderer(precision);
			if (imageGenerator == null) init();
			imageGenerator.setFont(font);
		}

		public ColorLegend(final ColorTableMapper mapper) {
			this(mapper, new Font(Font.SANS_SERIF, Font.PLAIN, (int) (12 * Prefs.SCALE_FACTOR)), 5, 2);
		}

		public void update(final double min, final double max) {
			shape.getColorMapper().setMin(min);
			shape.getColorMapper().setMax(max);
			((ColorbarImageGenerator) imageGenerator).setMin(min);
			((ColorbarImageGenerator) imageGenerator).setMax(max);
		}

		public Shape get() {
			return shape;
		}

		private void init() {
			initImageGenerator(shape, provider, renderer);
		}

		private void updateColors() {
			setBackground(view.getBackgroundColor());
			setForeground(view.getBackgroundColor().negative());
		}

		@Override
		public void initImageGenerator(final AbstractDrawable parent,
			final ITickProvider provider, final ITickRenderer renderer)
		{
			if (shape != null) imageGenerator = new ColorbarImageGenerator(shape
				.getColorMapper(), provider, renderer, font.getSize());
		}

	}

	private class ColorbarImageGenerator extends AWTColorbarImageGenerator {

		public ColorbarImageGenerator(final ColorMapper mapper,
			final ITickProvider provider, final ITickRenderer renderer,
			final int textSize)
		{
			super(mapper, provider, renderer);
			this.textSize = textSize;
		}

		@Override
		public BufferedImage toImage(final int width, final int height,
			final int barWidth)
		{
			if (barWidth > width) return null;
			BufferedImage image = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphic = image.createGraphics();
			configureText(graphic);
			final int maxWidth = graphic.getFontMetrics().stringWidth(renderer.format(
				max)) + barWidth + 1;
			// do we have enough space to display labels?
			if (maxWidth > width) {
				graphic.dispose();
				image.flush();
				image = new BufferedImage(maxWidth, height,
					BufferedImage.TYPE_INT_ARGB);
				graphic = image.createGraphics();
				configureText(graphic);
			}
			graphic.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			graphic.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			drawBackground(width, height, graphic);
			drawBarColors(height, barWidth, graphic);
			drawBarContour(height, barWidth, graphic);
			drawTextAnnotations(height, barWidth, graphic);
			return image;
		}

		private void setMin(final double min) {
			this.min = min;
		}

		private void setMax(final double max) {
			this.max = max;
		}
	}

	/**
	 * Adapted AWTView so that top/side views better match to coronal/sagittal
	 * ones
	 */
	private class AView extends AWTView {

		public AView(final IChartComponentFactory factory, final Scene scene,
			final ICanvas canvas, final Quality quality)
		{
			super(factory, scene, canvas, quality);
			//super.DISPLAY_AXE_WHOLE_BOUNDS = true;
			//super.MAINTAIN_ALL_OBJECTS_IN_VIEW = true;
			//setBoundMode(ViewBoundMode.AUTO_FIT);
		}

		private void logViewPoint() {
			final StringBuilder sb = new StringBuilder("setViewPoint(");
			sb.append(viewpoint.x).append("f, ");
			sb.append(viewpoint.y).append("f);");
			SNT.log(sb.toString());
		}

		public void setBoundManual(final BoundingBox3d bounds,
			final boolean logInDebugMode)
		{
			super.setBoundManual(bounds);
			if (logInDebugMode && SNT.isDebugMode()) {
				final StringBuilder sb = new StringBuilder("setBounds(");
				sb.append(bounds.getXmin()).append("f, ");
				sb.append(bounds.getXmax()).append("f, ");
				sb.append(bounds.getYmin()).append("f, ");
				sb.append(bounds.getYmax()).append("f, ");
				sb.append(bounds.getZmin()).append("f, ");
				sb.append(bounds.getZmax()).append("f);");
				SNT.log(sb.toString());
			}
		}

		@Override
		protected Coord3d computeCameraEyeTop(final Coord3d viewpoint,
			final Coord3d target)
		{
			Coord3d eye = viewpoint;
			eye.x = -(float) Math.PI / 2; // on x
			eye.y = -(float) Math.PI / 2; // on bottom
			eye = eye.cartesian().add(target);
			return eye;
		}
	}

	// NB: MouseContoller does not seem to work with FrameSWing so we are stuck
	// with AWT
	private class ViewerFrame extends FrameAWT implements IFrame {

		private static final long serialVersionUID = 1L;
		private static final int DEF_WIDTH = 800;
		private static final int DEF_HEIGHT = 600;

		private Chart chart;
		private Component canvas;
		private JDialog manager;
		private AllenCCFNavigator allenNavigator;
		private ManagerPanel managerPanel;

		/**
		 * Instantiates a new viewer frame.
		 *
		 * @param chart the chart to be rendered in the frame
		 * @param includeManager whether the "Reconstruction Viewer Manager" dialog
		 *          should be made visible
		 */
		public ViewerFrame(final Chart chart, final boolean includeManager) {
			this(chart, (int) (DEF_WIDTH * Prefs.SCALE_FACTOR), (int) (DEF_HEIGHT * Prefs.SCALE_FACTOR),
					includeManager);
		}

		public ViewerFrame(final Chart chart, final int width, final int height, final boolean includeManager) {
			final String title = (isSNTInstance()) ? " (SNT)" : "";
			initialize(chart, new Rectangle(width, height), "Reconstruction Viewer" +
				title);
			setLocationRelativeTo(null); // ensures frame will not appear in between displays on a multidisplay setup
			if (includeManager) {
				manager = getManager();
				managerList.selectAll();
				manager.setVisible(true);
			}
			toFront();
		}

		public void replaceCurrentChart(final Chart chart) {
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			removeAll();
			add(canvas);
			// doLayout();
			revalidate();
			// update(getGraphics());
		}

		public JDialog getManager() {
			final JDialog dialog = new JDialog(this, "RV Controls");
			dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			// dialog.setLocationRelativeTo(this);
			final java.awt.Point parentLoc = getLocation();
			dialog.setLocation(parentLoc.x + getWidth() + 5, parentLoc.y);
			managerPanel = new ManagerPanel(new GuiUtils(dialog));
			dialog.setContentPane(managerPanel);
			dialog.pack();
			return dialog;
		}

		/* (non-Javadoc)
		 * @see org.jzy3d.bridge.awt.FrameAWT#initialize(org.jzy3d.chart.Chart, org.jzy3d.maths.Rectangle, java.lang.String)
		 */
		@Override
		public void initialize(final Chart chart, final Rectangle bounds,
			final String title)
		{
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			setTitle(title);
			add(canvas);
			pack();
			setSize(new Dimension(bounds.width, bounds.height));
			AWTWindows.centerWindow(this);
			addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(final WindowEvent e) {
					getOuter().dispose();
				}
			});
			setVisible(true);
		}

		public void disposeFrame() {
			ViewerFrame.this.remove(canvas);
			ViewerFrame.this.chart.dispose();
			ViewerFrame.this.chart = null;
			if (ViewerFrame.this.manager != null) ViewerFrame.this.manager.dispose();
			ViewerFrame.this.dispose();
		}

		/* (non-Javadoc)
		 * @see org.jzy3d.bridge.awt.FrameAWT#initialize(org.jzy3d.chart.Chart, org.jzy3d.maths.Rectangle, java.lang.String, java.lang.String)
		 */
		@Override
		public void initialize(final Chart chart, final Rectangle bounds,
			final String title, final String message)
		{
			initialize(chart, bounds, title + message);
		}
	}

	private static class Prefs {

		/* Pan accuracy control */
		private enum PAN {
				LOW(.25f, "Low"), //
				MEDIUM(.5f, "Medium"), //
				HIGH(1f, "High"), //
				HIGHEST(2.5f, "Highest");

			private static final float DEF_PAN_STEP = 1f;
			private final float step;
			private final String description;

			// the lowest the step the more responsive the pan
			PAN(final float step, final String description) {
				this.step = step;
				this.description = description;
			}
		}

		/* Zoom control */
		private static final float[] ZOOM_STEPS = new float[] { .01f, .05f, .1f,
			.2f };
		private static final float DEF_ZOOM_STEP = ZOOM_STEPS[1];

		/* Rotation control */
		private static final double[] ROTATION_STEPS = new double[] { Math.PI / 180,
			Math.PI / 36, Math.PI / 18, Math.PI / 6 }; // 1, 5, 10, 30 degrees
		private static final double DEF_ROTATION_STEP = ROTATION_STEPS[1];

		/* GUI */
		private static final double SCALE_FACTOR = ij.Prefs.getDouble("gui.scale", 1d); //TODO: Replace w/ Prefs.getGuiScale(); when IJ1.52k in POM
		private static final boolean DEF_NAG_USER_ON_RETRIEVE_ALL = true;
		private static final boolean DEF_RETRIEVE_ALL_IF_NONE_SELECTED = true;
		public boolean nagUserOnRetrieveAll;
		public boolean retrieveAllIfNoneSelected;

		private final Viewer3D tp;
		private final KeyController kc;
		private final MouseController mc;
		private String storedSensitivity;
		private String snapshotDir;

		public Prefs(final Viewer3D tp) {
			this.tp = tp;
			kc = tp.keyController;
			mc = tp.mouseController;
		}

		private void setPreferences() {
			nagUserOnRetrieveAll = DEF_NAG_USER_ON_RETRIEVE_ALL;
			retrieveAllIfNoneSelected = DEF_RETRIEVE_ALL_IF_NONE_SELECTED;
			setSnapshotDirectory();
			if (tp.prefService == null) {
				kc.zoomStep = DEF_ZOOM_STEP;
				kc.rotationStep = DEF_ROTATION_STEP;
				mc.panStep = PAN.DEF_PAN_STEP;
			}
			else {
				kc.zoomStep = getZoomStep();
				kc.rotationStep = getRotationStep();
				mc.panStep = getPanStep();
				storedSensitivity = null;
			}
		}

		private void setSnapshotDirectory() {
			snapshotDir = (tp.prefService == null) ? RecViewerPrefsCmd.DEF_SNAPSHOT_DIR
			: tp.prefService.get(RecViewerPrefsCmd.class, "snapshotDir",
				RecViewerPrefsCmd.DEF_SNAPSHOT_DIR);
			final File dir = new File(snapshotDir);
			if (!dir.exists() || !dir.isDirectory()) dir.mkdirs();
		}

		private float getSnapshotRotationAngle() {
			return tp.prefService.getFloat(RecViewerPrefsCmd.class, "rotationAngle",
				RecViewerPrefsCmd.DEF_ROTATION_ANGLE);
		}

		private int getSnapshotRotationSteps() {
			final int fps = tp.prefService.getInt(RecViewerPrefsCmd.class,
				"rotationFPS", RecViewerPrefsCmd.DEF_ROTATION_FPS);
			final double duration = tp.prefService.getDouble(RecViewerPrefsCmd.class,
				"rotationDuration", RecViewerPrefsCmd.DEF_ROTATION_DURATION);
			return (int) Math.round(fps * duration);
		}

		private String getControlsSensitivity() {
			return (storedSensitivity == null) ? tp.prefService.get(
				RecViewerPrefsCmd.class, "sensitivity",
				RecViewerPrefsCmd.DEF_CONTROLS_SENSITIVITY) : storedSensitivity;
		}

		private float getPanStep() {
			switch (getControlsSensitivity()) {
				case "Highest":
					return PAN.HIGHEST.step;
				case "Hight":
					return PAN.HIGH.step;
				case "Medium":
					return PAN.MEDIUM.step;
				case "Low":
					return PAN.LOW.step;
				default:
					return PAN.DEF_PAN_STEP;
			}
		}

		private float getZoomStep() {
			switch (getControlsSensitivity()) {
				case "Highest":
					return ZOOM_STEPS[0];
				case "Hight":
					return ZOOM_STEPS[1];
				case "Medium":
					return ZOOM_STEPS[2];
				case "Low":
					return ZOOM_STEPS[3];
				default:
					return DEF_ZOOM_STEP;
			}
		}

		private double getRotationStep() {
			switch (getControlsSensitivity()) {
				case "Highest":
					return ROTATION_STEPS[0];
				case "Hight":
					return ROTATION_STEPS[1];
				case "Medium":
					return ROTATION_STEPS[2];
				case "Low":
					return ROTATION_STEPS[3];
				default:
					return DEF_ROTATION_STEP;
			}
		}

	}

	private class ManagerPanel extends JPanel {

		private static final long serialVersionUID = 1L;
		private final GuiUtils guiUtils;
		private DefaultGenericTable table;
		private JCheckBoxMenuItem debugCheckBox;
		private final JPanel barPanel;
		private final SNTSearchableBar searchableBar;

		public ManagerPanel(final GuiUtils guiUtils) {
			super();
			this.guiUtils = guiUtils;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			searchableBar = new SNTSearchableBar(new ListSearchable(managerList));
			searchableBar.setStatusLabelPlaceholder(String.format(
				"%d Item(s) listed", managerModel.size()));
			searchableBar.setVisibleButtons(SearchableBar.SHOW_CLOSE |
				SearchableBar.SHOW_NAVIGATION | SearchableBar.SHOW_HIGHLIGHTS |
				SearchableBar.SHOW_MATCHCASE | SearchableBar.SHOW_STATUS);
			barPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			setFixedHeightToPanel(barPanel);
			barPanel.add(searchableBar);
			barPanel.setVisible(false);
			searchableBar.setInstaller(new SearchableBar.Installer() {

				@Override
				public void openSearchBar(final SearchableBar searchableBar) {
					final Container dialog = getRootPane().getParent();
					dialog.setSize(searchableBar.getWidth(), dialog.getHeight());
					barPanel.setVisible(true);
					searchableBar.focusSearchField();
				}

				@Override
				public void closeSearchBar(final SearchableBar searchableBar) {
					final Container dialog = getRootPane().getParent();
					barPanel.setVisible(false);
					dialog.setSize(getPreferredSize().width, dialog.getHeight());
				}
			});
			final JScrollPane scrollPane = new JScrollPane(managerList);
			managerList.setComponentPopupMenu(popupMenu());
			scrollPane.setWheelScrollingEnabled(true);
			scrollPane.setBorder(null);
			scrollPane.setViewportView(managerList);
			add(scrollPane);
			scrollPane.revalidate();
			add(barPanel);
			add(buttonPanel());
		}

		private JPanel buttonPanel() {
			final boolean includeAnalysisCmds = !isSNTInstance();
			final JPanel buttonPanel = new JPanel(new GridLayout(1,
				(includeAnalysisCmds) ? 4 : 5));
			buttonPanel.setBorder(null);
			// do not allow panel to resize vertically
			setFixedHeightToPanel(buttonPanel);
			buttonPanel.add(menuButton(GLYPH.MASKS, sceneMenu(), "Scene Controls"));
			buttonPanel.add(menuButton(GLYPH.TREE, treesMenu(),
				"Manage & Customize Neuronal Arbors"));
			buttonPanel.add(menuButton(GLYPH.CUBE, meshMenu(), "Manage & Customize 3D Meshes"));
			if (includeAnalysisCmds) buttonPanel.add(menuButton(GLYPH.CALCULATOR,
				measureMenu(), "Analyze & Measure"));
			buttonPanel.add(menuButton(GLYPH.TOOL, toolsMenu(), "Tools & Utilities"));
			return buttonPanel;
		}

		private void setFixedHeightToPanel(final JPanel panel) {
			// do not allow panel to resize vertically
			panel.setMaximumSize(new Dimension(panel
				.getMaximumSize().width, (int) panel.getPreferredSize()
					.getHeight()));
		}

		private JButton menuButton(final GLYPH glyph, final JPopupMenu menu,
			final String tooltipMsg)
		{
			final JButton button = new JButton(IconFactory.getButtonIcon(glyph));
			button.setToolTipText(tooltipMsg);
			button.addActionListener(e -> menu.show(button, button.getWidth() / 2,
				button.getHeight() / 2));
			return button;
		}

		private JPopupMenu sceneMenu() {
			final JPopupMenu sceneMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Fit to Visible Objects", IconFactory
				.getMenuIcon(GLYPH.EXPAND));
			mi.setMnemonic('f');
			mi.addActionListener(e -> {
				fitToVisibleObjects(true);
				final BoundingBox3d bounds = chart.view().getScene().getGraph().getBounds();
				final StringBuilder sb = new StringBuilder();
				sb.append("X: ").append(String.format("%.2f", bounds.getXmin())).append(
					"-").append(String.format("%.2f", bounds.getXmax()));
				sb.append(" Y: ").append(String.format("%.2f", bounds.getYmin()))
					.append("-").append(String.format("%.2f", bounds.getYmax()));
				sb.append(" Z: ").append(String.format("%.2f", bounds.getZmin()))
					.append("-").append(String.format("%.2f", bounds.getZmax()));
				displayMsg("Zoomed to " + sb.toString());
			});
			sceneMenu.add(mi);

			// Aspect-ratio controls
			final JMenuItem jcbmiFill = new JCheckBoxMenuItem("Stretch-to-Fill");
			jcbmiFill.setIcon(IconFactory.getMenuIcon(GLYPH.EXPAND_ARROWS));
			jcbmiFill.addItemListener(e -> {
				final ViewportMode mode = (jcbmiFill.isSelected()) ? ViewportMode.STRETCH_TO_FILL : ViewportMode.RECTANGLE_NO_STRETCH;
				view.getCamera().setViewportMode(mode);
			});
			sceneMenu.add(jcbmiFill);
			sceneMenu.add(squarifyMenu());
			sceneMenu.addSeparator();

			mi = new JMenuItem("Reload Scene", IconFactory.getMenuIcon(GLYPH.REDO));
			mi.setMnemonic('r');
			mi.addActionListener(e -> {
				if (!sceneIsOK() && guiUtils.getConfirmation(
					"Scene was reloaded but some objects have invalid attributes. "//
				+ "Rebuild 3D Scene Completely?", "Rebuild Required")) {
					rebuild();
				}
				else {
					displayMsg("Scene reloaded");
				}
			});
			sceneMenu.add(mi);

			mi = new JMenuItem("Rebuild Scene...", IconFactory.getMenuIcon(GLYPH.RECYCLE));
			mi.addActionListener(e -> {
				if (guiUtils.getConfirmation("Rebuild 3D Scene Completely?",
					"Force Rebuild"))
				{
					rebuild();
				}
			});
			sceneMenu.add(mi);

			mi = new JMenuItem("Wipe Scene...", IconFactory.getMenuIcon(GLYPH.BROOM));
			mi.addActionListener(e -> {
				if (guiUtils.getConfirmation(
					"Remove all items from scene? This action cannot be undone.",
					"Wipe Scene?"))
				{
					getOuter().removeAll();
					removeAllOBJs();
					removeColorLegends(false);
					// Ensure nothing else remains (e.g., a Delaunay surface)
					chart.getScene().getGraph().getAll().clear();
				}
			});
			sceneMenu.add(mi);
			sceneMenu.addSeparator();

			mi = new JMenuItem("Sync Path Manager Changes", IconFactory.getMenuIcon(
				GLYPH.SYNC));
			mi.setMnemonic('s');
			mi.addActionListener(e -> {
				try {
					if (!syncPathManagerList()) rebuild();
					displayMsg("Path Manager contents updated");
				}
				catch (final IllegalArgumentException ex) {
					guiUtils.error(ex.getMessage());
				}
			});
			mi.setEnabled(isSNTInstance());
			sceneMenu.add(mi);
			return sceneMenu;
		}

		private JMenu squarifyMenu() {
			final JMenu menu = new JMenu("Impose Isotropic Scale");
			menu.setIcon(IconFactory.getMenuIcon(GLYPH.EQUALS));
			final ButtonGroup cGroup = new ButtonGroup();
			final String[] axes = new String[] { "XY", "ZY", "XZ", "None"};
			for (final String axis : axes) {
				final JMenuItem jcbmi = new JCheckBoxMenuItem(axis, axis.startsWith("None"));
				cGroup.add(jcbmi);
				jcbmi.addItemListener(e -> squarify(axis, jcbmi.isSelected()));
				menu.add(jcbmi);
			}
			return menu;
		}

		private JPopupMenu popupMenu() {
			final JMenuItem sort = new JMenuItem("Sort List", IconFactory.getMenuIcon(
				GLYPH.SORT));
			sort.addActionListener(e -> {
				final List<String> checkedLabels = getLabelsCheckedInManager();
				try {
					managerList.setValueIsAdjusting(true);
					managerModel.removeAllElements();
					plottedTrees.keySet().forEach(k -> {
						managerModel.addElement(k);
					});
					plottedObjs.keySet().forEach(k -> {
						managerModel.addElement(k);
					});
					managerModel.addElement(CheckBoxList.ALL_ENTRY);
				}
				finally {
					managerList.setValueIsAdjusting(false);
				}
				managerList.addCheckBoxListSelectedValues(checkedLabels.toArray());
			});

			// Select menu
			final JMenu selectMenu = new JMenu("Select");
			selectMenu.setIcon(IconFactory.getMenuIcon(GLYPH.POINTER));
			final JMenuItem selectMeshes = new JMenuItem("Meshes");
			selectMeshes.addActionListener(e -> selectRows(plottedObjs));
			selectMenu.add(selectMeshes);
			final JMenuItem selectTrees = new JMenuItem("Trees");
			selectTrees.addActionListener(e -> selectRows(plottedTrees));
			selectMenu.add(selectTrees);
			selectMenu.addSeparator();
			final JMenuItem selectNone = new JMenuItem("None");
			selectNone.addActionListener(e -> managerList.clearSelection());
			selectMenu.add(selectNone);

			// Hide menu
			final JMenu hideMenu = new JMenu("Hide");
			hideMenu.setIcon(IconFactory.getMenuIcon(GLYPH.EYE_SLASH));
			final JMenuItem hideMeshes = new JMenuItem("Meshes");
			hideMeshes.addActionListener(e -> retainVisibility(plottedTrees));
			hideMenu.add(hideMeshes);
			final JMenuItem hideTrees = new JMenuItem("Trees");
			hideTrees.addActionListener(e -> {
				setArborsDisplayed(getLabelsCheckedInManager(), false);
			});
			final JMenuItem hideSomas = new JMenuItem("Somas of Visible Trees");
			hideSomas.addActionListener(e -> displaySomas(false));
			hideMenu.add(hideSomas);
			final JMenuItem hideAll = new JMenuItem("All");
			hideAll.addActionListener(e -> managerList.selectNone());
			hideMenu.addSeparator();
			hideMenu.add(hideAll);

			// Show Menu
			final JMenu showMenu = new JMenu("Show");
			showMenu.setIcon(IconFactory.getMenuIcon(GLYPH.EYE));
			final JMenuItem showMeshes = new JMenuItem("Meshes");
			showMeshes.addActionListener(e -> managerList
				.addCheckBoxListSelectedValues(plottedObjs.keySet().toArray()));
			showMenu.add(showMeshes);
			final JMenuItem showTrees = new JMenuItem("Trees");
			showTrees.addActionListener(e -> managerList
				.addCheckBoxListSelectedValues(plottedTrees.keySet().toArray()));
			showMenu.add(showTrees);
			final JMenuItem showSomas = new JMenuItem("Somas of Visible Trees");
			showSomas.addActionListener(e -> displaySomas(true));
			showMenu.add(showSomas);
			final JMenuItem showAll = new JMenuItem("All");
			showAll.addActionListener(e -> managerList.selectAll());
			showMenu.add(showAll);

			final JMenuItem find = new JMenuItem("Find...", IconFactory.getMenuIcon(
				GLYPH.BINOCULARS));
			find.addActionListener(e -> {
					//managerList.clearSelection();
					searchableBar.getInstaller().openSearchBar(searchableBar);
					searchableBar.focusSearchField();
			});

			final JPopupMenu pMenu = new JPopupMenu();
			pMenu.add(selectMenu);
			pMenu.add(showMenu);
			pMenu.add(hideMenu);
			pMenu.addSeparator();
			pMenu.add(find);
			pMenu.addSeparator();
			pMenu.add(sort);
			return pMenu;
		}

		private void displaySomas(final boolean displayed) {
			final List<String> labels = getLabelsCheckedInManager();
			if (labels.isEmpty()) {
				displayMsg("There are no visible reconstructions");
				return;
			}
			setSomasDisplayed(labels, displayed);
		}

		private void selectRows(final Map<String, ?> map) {
			final int[] indices = new int[map.keySet().size()];
			int i = 0;
			for (final String k : map.keySet()) {
				indices[i++] = managerModel.indexOf(k);
			}
			managerList.setSelectedIndices(indices);
		}

		private void retainVisibility(final Map<String, ?> map) {
			final List<String> selectedKeys = new ArrayList<>(
				getLabelsCheckedInManager());
			selectedKeys.retainAll(map.keySet());
			managerList.setSelectedObjects(selectedKeys.toArray());
		}

		private List<String> getSelectedTrees(final boolean promptForAllIfNone) {
			return getSelectedKeys(plottedTrees, "reconstructions",
				promptForAllIfNone);
		}

		private List<String> getSelectedMeshes(final boolean promptForAllIfNone) {
			return getSelectedKeys(plottedObjs, "meshes", promptForAllIfNone);
		}

		private List<String> getSelectedKeys(final Map<String, ?> map,
			final String mapDescriptor, final boolean promptForAllIfNone)
		{
			if (map.isEmpty()) {
				guiUtils.error("There are no loaded " + mapDescriptor + ".");
				return null;
			}
			final List<?> selectedKeys = managerList.getSelectedValuesList();
			final List<String> allKeys = new ArrayList<>(map.keySet());
			if ((promptForAllIfNone && map.size() == 1) || (selectedKeys
				.size() == 1 && selectedKeys.get(0) == CheckBoxList.ALL_ENTRY))
				return allKeys;
			if (promptForAllIfNone && selectedKeys.isEmpty()) {
				checkRetrieveAllOptions(mapDescriptor);
				if (prefs.retrieveAllIfNoneSelected) return allKeys;
				guiUtils.error("There are no selected " + mapDescriptor + ".");
				return null;
			}
			allKeys.retainAll(selectedKeys);
			return allKeys;
		}

		private void checkRetrieveAllOptions(final String mapDescriptor) {
			if (!prefs.nagUserOnRetrieveAll) return;
			final boolean[] options = guiUtils.getPersistentConfirmation(
				"There are no items selected. "//
					+ "Apply changes to all " + mapDescriptor + "?", "Apply to All?");
			prefs.retrieveAllIfNoneSelected = options[0];
			prefs.nagUserOnRetrieveAll = !options[1];
		}

		private JPopupMenu measureMenu() {
			final JPopupMenu measureMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Measure", IconFactory.getMenuIcon(
				GLYPH.TABLE));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null) return;
				if (table == null) table = new DefaultGenericTable();
				plottedTrees.forEach((k, shapeTree) -> {
					if (!keys.contains(k)) return;
					final TreeStatistics tStats = new TreeStatistics(shapeTree.tree);
					tStats.setTable(table);
					tStats.summarize(k, true);
				});
				final List<Display<?>> displays = displayService.getDisplays(table);
				if (displays == null || displays.isEmpty()) {
					displayService.createDisplay("RV Measurements", table);
				}
				else {
					displays.forEach(d -> d.update());
				}
			});
			measureMenu.add(mi);
			mi = new JMenuItem("Distribution Analysis...", IconFactory.getMenuIcon(
				GLYPH.CHART));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null || keys.isEmpty()) return;
				final Tree mergedTree = new Tree();
				plottedTrees.forEach((k, shapeTree) -> {
					if (!keys.contains(k)) return;
					mergedTree.merge(shapeTree.tree);
				});
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("tree", mergedTree);
				runCmd(DistributionCmd.class, inputs, CmdWorker.DO_NOTHING, false);
			});
			measureMenu.add(mi);
			mi = new JMenuItem("Sholl Analysis...", IconFactory.getMenuIcon(
				GLYPH.BULLSEYE));
			mi.addActionListener(e -> {
				final Tree tree = getSingleSelectionTree();
				if (tree == null) return;
				final Map<String, Object> input = new HashMap<>();
				input.put("snt", null);
				input.put("tree", tree);
				runCmd(ShollTracingsCmd.class, input, CmdWorker.DO_NOTHING, false);
			});
			measureMenu.add(mi);
			mi = new JMenuItem("Strahler Analysis", IconFactory.getMenuIcon(
				GLYPH.BRANCH_CODE));
			mi.addActionListener(e -> {
				final Tree tree = getSingleSelectionTree();
				if (tree == null) return;
				final StrahlerCmd sa = new StrahlerCmd(tree);
				sa.setContext(context);
				sa.setTable(new DefaultGenericTable(),
					"SNT: Horton-Strahler Analysis " + tree.getLabel());
				SwingUtilities.invokeLater(() -> sa.run());
			});
			measureMenu.add(mi);
			return measureMenu;
		}

		private Tree getSingleSelectionTree() {
			if (plottedTrees.size() == 1) return plottedTrees.values().iterator()
				.next().tree;
			final List<String> keys = getSelectedTrees(false);
			if (keys == null) return null;
			if (keys.isEmpty() || keys.size() > 1) {
				guiUtils.error(
					"This command requires a single recontruction to be selected.");
				return null;
			}
			return plottedTrees.get(keys.get(0)).tree;
		}

		private JMenu customizeMeshesMenu() {
			final JMenu meshMenu = new JMenu("Customize");
			meshMenu.setMnemonic('c');
			meshMenu.setIcon(IconFactory.getMenuIcon(GLYPH.CUBE));

			// Mesh customizations
			JMenuItem mi = new JMenuItem("Assign Color...", IconFactory.getMenuIcon(
				GLYPH.COLOR));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedMeshes(true);
				if (keys == null) return;
				final java.awt.Color c = guiUtils.getColor("Mesh(es) Color",
					java.awt.Color.WHITE, "HSB");
				if (c == null) {
					return; // user pressed cancel
				}
				final Color color = fromAWTColor(c);
				for (final String label : keys) {
					plottedObjs.get(label).setColor(color);
				}
			});
			meshMenu.add(mi);
			mi = new JMenuItem("Transparency...", IconFactory.getMenuIcon(
				GLYPH.ADJUST));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedMeshes(true);
				if (keys == null) return;
				final Double t = guiUtils.getDouble("Mesh Transparency (%)",
					"Transparency (%)", 95);
				if (t == null) {
					return; // user pressed cancel
				}
				final float fValue = 1 - (t.floatValue() / 100);
				if (Float.isNaN(fValue) || fValue <= 0 || fValue >= 1) {
					guiUtils.error("Invalid transparency value: Only ]0, 100[ accepted.");
					return;
				}
				for (final String label : keys) {
					plottedObjs.get(label).getColor().a = fValue;
				}
			});
			meshMenu.add(mi);
			return meshMenu;
		}

		private JMenu customizeTreesMenu() {
			final JMenu menu = new JMenu("Customize");
			menu.setMnemonic('c');
			menu.setIcon(IconFactory.getMenuIcon(GLYPH.TREE));

			JMenuItem mi = new JMenuItem("Assign Color...", IconFactory.getMenuIcon(GLYPH.COLOR));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null || !okToApplyColor(keys)) return;
				final ColorRGB c = guiUtils.getColorRGB("Reconstruction(s) Color",
					java.awt.Color.RED, "HSB");
				if (c == null) {
					return; // user pressed cancel
				}
				applyColorToPlottedTrees(keys, c);
			});
			menu.add(mi);

			mi = new JMenuItem("Color Coding (Individual Cells)...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null) return;
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("treeMappingLabels", keys);
				runCmd(ColorMapReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			menu.add(mi);
			mi = new JMenuItem("Color Coding (Group of Cells)...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null) return;
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("multiTreeMappingLabels", keys);
				runCmd(ColorMapReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			menu.add(mi);
			mi = new JMenuItem("Color Each Cell Uniquely...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null || !okToApplyColor(keys)) return;

				final ColorRGB[] colors = SNTColor.getDistinctColors(keys.size());
				final int[] counter = new int[] { 0 };
				plottedTrees.forEach((k, shapeTree) -> {
					shapeTree.setArborColor(colors[counter[0]]);
					shapeTree.setSomaColor(colors[counter[0]]);
					counter[0]++;
				});
			});
			menu.add(mi);
			menu.addSeparator();

			mi = new JMenuItem("Thickness...", IconFactory.getMenuIcon(GLYPH.DOTCIRCLE));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(true);
				if (keys == null) return;
				String msg = "<HTML><body><div style='width:500;'>" +
					"Please specify a constant thickness to be applied " +
					"to selected " + keys.size() + " reconstruction(s).";
				if (isSNTInstance()) {
					msg += " This value will only affect how Paths are displayed " +
						"in the Reconstruction Viewer.";
				}
				final Double thickness = guiUtils.getDouble(msg, "Path Thickness",
					getDefaultThickness());
				if (thickness == null) {
					return; // user pressed cancel
				}
				if (Double.isNaN(thickness) || thickness <= 0) {
					guiUtils.error("Invalid thickness value.");
					return;
				}
				setTreesThickness(keys, thickness.floatValue());
			});
			menu.add(mi);

			mi = new JMenuItem("Translate...", IconFactory.getMenuIcon(GLYPH.MOVE));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(prefs.retrieveAllIfNoneSelected);
				if (keys == null) return;
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("treeLabels", keys);
				runCmd(TranslateReconstructionsCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			menu.add(mi);
			return menu;
		}

		private JPopupMenu toolsMenu() {
			final JPopupMenu settingsMenu = new JPopupMenu();
			final JMenuItem jcbmi = new JCheckBoxMenuItem("Debug Mode", SNT.isDebugMode());
			jcbmi.setEnabled(!isSNTInstance());jcbmi.setIcon(IconFactory.getMenuIcon(GLYPH.BUG));
			jcbmi.setMnemonic('d');
			jcbmi.addItemListener(e -> {
				if (isSNTInstance()) {
					sntService.getPlugin().getUI().setEnableDebugMode(jcbmi.isSelected());
				} else {
					SNT.setDebugMode(jcbmi.isSelected());
				}
			});
			settingsMenu.add(jcbmi);
			settingsMenu.addSeparator();

			JMenuItem mi = new JMenuItem("Take Snapshot", IconFactory
				.getMenuIcon(GLYPH.CAMERA));
			mi.addActionListener(e -> keyController.saveScreenshot());
			settingsMenu.add(mi);
			mi = new JMenuItem("Record Rotation", IconFactory
				.getMenuIcon(GLYPH.VIDEO));
			mi.addActionListener(e -> {
				SwingUtilities.invokeLater(() -> {
					displayMsg("Recording rotation...", 0);
					new RecordWorker().execute();
				});
			});
			settingsMenu.add(mi);
			settingsMenu.addSeparator();

			settingsMenu.add(legendMenu());
			settingsMenu.addSeparator();

			settingsMenu.add(sensitivityMenu());
			mi = new JMenuItem("Keyboard Shortcuts...", IconFactory.getMenuIcon(
				GLYPH.KEYBOARD));
			mi.addActionListener(e -> keyController.showHelp(true));
			settingsMenu.add(mi);
			settingsMenu.addSeparator();
			mi = new JMenuItem("Preferences...", IconFactory.getMenuIcon(GLYPH.COG));
			mi.addActionListener(e -> {
				runCmd(RecViewerPrefsCmd.class, null, CmdWorker.RELOAD_PREFS, false);
			});
			settingsMenu.add(mi);
			return settingsMenu;
		}

		private JMenu sensitivityMenu() {
			final JMenu zoomMenu = new JMenu("Zoom Steps");
			zoomMenu.setIcon(IconFactory.getMenuIcon(GLYPH.SEARCH));
			final ButtonGroup zGroup = new ButtonGroup();
			for (final float step : Prefs.ZOOM_STEPS) {
				final JMenuItem jcbmi = new JCheckBoxMenuItem(String.format("%.0f",
					step * 100) + "%");
				jcbmi.setSelected(step == keyController.zoomStep);
				jcbmi.addItemListener(e -> keyController.zoomStep = step);
				zGroup.add(jcbmi);
				zoomMenu.add(jcbmi);
			}
			final JMenu rotationMenu = new JMenu("Rotation Steps");
			rotationMenu.setIcon(IconFactory.getMenuIcon(GLYPH.UNDO));
			final ButtonGroup rGroup = new ButtonGroup();
			for (final double step : Prefs.ROTATION_STEPS) {
				final JMenuItem jcbmi = new JCheckBoxMenuItem(String.format("%.1f", Math
					.toDegrees(step)) + "\u00b0");
				jcbmi.setSelected(step == keyController.rotationStep);
				jcbmi.addItemListener(e -> keyController.rotationStep = step);
				rGroup.add(jcbmi);
				rotationMenu.add(jcbmi);
			}
			final JMenu panMenu = new JMenu("Pan Accuracy");
			panMenu.setIcon(IconFactory.getMenuIcon(GLYPH.HAND));
			final ButtonGroup pGroup = new ButtonGroup();
			for (final Prefs.PAN pan : Prefs.PAN.values()) {
				final JMenuItem jcbmi = new JCheckBoxMenuItem(pan.description);
				jcbmi.setSelected(pan.step == mouseController.panStep);
				jcbmi.addItemListener(e -> mouseController.panStep = pan.step);
				pGroup.add(jcbmi);
				panMenu.add(jcbmi);
			}
			final JMenu sensitivityMenu = new JMenu("Keyboard & Mouse Sensitivity");
			sensitivityMenu.setIcon(IconFactory.getMenuIcon(GLYPH.CHECK_DOUBLE));
			sensitivityMenu.add(panMenu);
			sensitivityMenu.add(rotationMenu);
			sensitivityMenu.add(zoomMenu);
			return sensitivityMenu;
		}

		private class RecordWorker extends SwingWorker<String, Object> {

			private boolean error = false;

			@Override
			protected String doInBackground() {
				final File rootDir = new File(prefs.snapshotDir +
					File.separator + "SNTrecordings");
				if (!rootDir.exists()) rootDir.mkdirs();
				final int dirId = rootDir.list((current, name) -> new File(current,
					name).isDirectory() && name.startsWith("recording")).length + 1;
				final File dir = new File(rootDir + File.separator + "recording" +
					String.format("%01d", dirId));
				try {
					recordRotation(prefs.getSnapshotRotationAngle(), prefs
						.getSnapshotRotationSteps(), dir);
					return "Finished. Frames at " + dir.getParent();
				}
				catch (final IllegalArgumentException | SecurityException ex) {
					error = true;
					return ex.getMessage();
				}
			}

			@Override
			protected void done() {
				String doneMessage;
				try {
					doneMessage = get();
				}
				catch (InterruptedException | ExecutionException ex) {
					error = true;
					doneMessage = "Unfortunately an exception occured.";
					if (SNT.isDebugMode())
						SNT.error("Recording failure", ex);
				}
				if (error) {
					displayMsg("Recording failure...");
					guiUtils.error(doneMessage);
				}
				else displayMsg(doneMessage);
			}
		}

		private boolean okToApplyColor(final List<String> labelsOfselectedTrees) {
			if (!treesContainColoredNodes(labelsOfselectedTrees)) return true;
			return guiUtils.getConfirmation("Some of the selected reconstructions " +
				"seem to be color-coded. Apply homogeneous color nevertheless?",
				"Override Color Code?");
		}

		private JPopupMenu treesMenu() {
			final JPopupMenu tracesMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Import File...", IconFactory.getMenuIcon(
				GLYPH.IMPORT));
			mi.setMnemonic('f');
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("importDir", false);
				runCmd(LoadReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			tracesMenu.add(mi);
			mi = new JMenuItem("Import Directory...", IconFactory.getMenuIcon(
				GLYPH.FOLDER));
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("importDir", true);
				runCmd(LoadReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			tracesMenu.add(mi);
			final JMenu remoteMenu = new JMenu("Load from Database");
			remoteMenu.setMnemonic('d');
			remoteMenu.setDisplayedMnemonicIndex(10);
			remoteMenu.setIcon(IconFactory.getMenuIcon(GLYPH.DATABASE));
			tracesMenu.add(remoteMenu);
			mi = new JMenuItem("FlyCircuit...", 'f');
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("loader", new FlyCircuitLoader());
				runCmd(RemoteSWCImporterCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			remoteMenu.add(mi);
			mi = new JMenuItem("MouseLight...", 'm');
			mi.addActionListener(e -> runCmd(MLImporterCmd.class, null,
				CmdWorker.DO_NOTHING));
			remoteMenu.add(mi);
			mi = new JMenuItem("NeuroMorpho...", 'n');
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("loader", new NeuroMorphoLoader());
				runCmd(RemoteSWCImporterCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			remoteMenu.add(mi);
			tracesMenu.addSeparator();
			tracesMenu.add(customizeTreesMenu());
			tracesMenu.addSeparator();
			mi = new JMenuItem("Remove Selected...", IconFactory.getMenuIcon(
				GLYPH.DELETE));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees(false);
				if (keys == null || keys.isEmpty()) {
					guiUtils.error("There are no selected reconstructions.");
					return;
				}
				if (!guiUtils.getConfirmation("Delete " + keys.size() +
					" reconstruction(s)?", "Confirm Deletion"))
				{
					return;
				}
				getOuter().setSceneUpdatesEnabled(false);
				keys.stream().forEach(k -> getOuter().remove(k));
				getOuter().setSceneUpdatesEnabled(true);
				getOuter().updateView();
			});
			tracesMenu.add(mi);
			mi = new JMenuItem("Remove All...", IconFactory.getMenuIcon(GLYPH.TRASH));
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all reconstructions from scene?",
					"Remove All Reconstructions?"))
				{
					return;
				}
				getOuter().removeAll();
			});
			tracesMenu.add(mi);
			return tracesMenu;
		}

		private JMenu legendMenu() {
			// Legend Menu
			final JMenu legendMenu = new JMenu("Color Legends");
			legendMenu.setIcon(IconFactory.getMenuIcon(GLYPH.COLOR2));
			JMenuItem mi = new JMenuItem("Add...");
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("treeMappingLabels", null);
				inputs.put("multiTreeMappingLabels", null);
				runCmd(ColorMapReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			legendMenu.add(mi);
			mi = new JMenuItem("Remove Last");
			mi.addActionListener(e -> removeColorLegends(true));
			legendMenu.add(mi);
			legendMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.setIcon(IconFactory.getMenuIcon(GLYPH.TRASH));
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all color legends from scene?",
					"Remove All Legends?"))
				{
					return;
				}
				removeColorLegends(false);
			});
			legendMenu.add(mi);
			return legendMenu;
		}

		private JPopupMenu meshMenu() {
			final JPopupMenu meshMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Allen CCF Navigator", IconFactory
					.getMenuIcon(GLYPH.NAVIGATE));
			mi.addActionListener(e -> {
				assert SwingUtilities.isEventDispatchThread();
				if (frame.allenNavigator != null) {
					frame.allenNavigator.dialog.toFront();
					return;
				}
				final JDialog tempSplash = frame.managerPanel.guiUtils.floatingMsg("Loading ontologies...", false);
				final SwingWorker<?, ?> worker = new SwingWorker<Object, Object>() {
					@Override
					protected Object doInBackground() {
						new AllenCCFNavigator().show();
						return null;
					}
					@Override
					protected void done() {
						tempSplash.dispose();
					}
				};
				worker.execute();
			});
			meshMenu.add(mi);
			mi = new JMenuItem("Import OBJ File(s)...", IconFactory
				.getMenuIcon(GLYPH.IMPORT));
			mi.addActionListener(e -> runCmd(LoadObjCmd.class, null,
				CmdWorker.DO_NOTHING));
			meshMenu.add(mi);
			final JMenu refMenu = new JMenu("Reference Brains");
			refMenu.setMnemonic('R');
			refMenu.setIcon(IconFactory.getMenuIcon(GLYPH.ATLAS));
			meshMenu.add(refMenu);
			mi = new JMenuItem("Mouse: Allen CCF");
			mi.setMnemonic('A');
			mi.addActionListener(e -> loadRefBrainAction(ALLEN_MESH_LABEL));
			refMenu.add(mi);
			mi = new JMenuItem("Drosophila: FlyCircuit");
			mi.addActionListener(e -> loadRefBrainAction(FCWB_MESH_LABEL));
			refMenu.add(mi);
			mi = new JMenuItem("Drosophila: JFRC2");
			mi.addActionListener(e -> loadRefBrainAction(JFRC2_MESH_LABEL));
			refMenu.add(mi);
			mi = new JMenuItem("Drosophila: JFRC3");
			mi.addActionListener(e -> loadRefBrainAction(JFRC3_MESH_LABEL));
			refMenu.add(mi);
			meshMenu.addSeparator();
			meshMenu.add(customizeMeshesMenu());
			meshMenu.addSeparator();
			mi = new JMenuItem("Remove Selected...", IconFactory.getMenuIcon(
				GLYPH.DELETE));
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedMeshes(false);
				if (keys == null || keys.isEmpty()) {
					guiUtils.error("There are no selected meshes.");
					return;
				}
				if (!guiUtils.getConfirmation("Delete " + keys.size() + " mesh(es)?",
					"Confirm Deletion"))
				{
					return;
				}
				getOuter().setSceneUpdatesEnabled(false);
				keys.stream().forEach(k -> getOuter().removeOBJ(k));
				getOuter().setSceneUpdatesEnabled(true);
				getOuter().updateView();
			});
			meshMenu.add(mi);
			mi = new JMenuItem("Remove All...");
			mi.setIcon(IconFactory.getMenuIcon(GLYPH.TRASH));
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all meshes from scene?",
					"Remove All Meshes?"))
				{
					return;
				}
				removeAllOBJs();
			});
			meshMenu.add(mi);
			return meshMenu;
		}

		private void loadRefBrainAction(final String label) {
			if (getOBJs().keySet().contains(label)) {
				guiUtils.error(label + " is already loaded.");
				managerList.addCheckBoxListSelectedValue(label, true);
				return;
			}
			try {
				loadRefBrain(label);
				getOuter().validate();
			}
			catch (final IllegalArgumentException ex) {
				guiUtils.error(ex.getMessage());
			}
		}

		private void removeColorLegends(final boolean justLastOne) {
			final List<AbstractDrawable> allDrawables = chart.getScene().getGraph()
				.getAll();
			final Iterator<AbstractDrawable> iterator = allDrawables.iterator();
			while (iterator.hasNext()) {
				final AbstractDrawable drawable = iterator.next();
				if (drawable != null && drawable.hasLegend() && drawable
					.isLegendDisplayed())
				{
					iterator.remove();
					if (justLastOne) break;
				}
			}
			cBar = null;
		}

		private void runCmd(final Class<? extends Command> cmdClass,
			final Map<String, Object> inputs, final int cmdType)
		{
			runCmd(cmdClass, inputs, cmdType, true);
		}

		private void runCmd(final Class<? extends Command> cmdClass,
			final Map<String, Object> inputs, final int cmdType,
			final boolean setRecViewerParamater)
		{
			if (cmdService == null) {
				guiUtils.error(
					"This command requires Reconstruction Viewer to be aware of a Scijava Context");
				return;
			}
			SwingUtilities.invokeLater(() -> {
				(new CmdWorker(cmdClass, inputs, cmdType, setRecViewerParamater))
					.execute();
			});
		}
	}


	private class AllenCCFNavigator {

		private final SNTSearchableBar searchableBar;
		private final DefaultTreeModel treeModel;
		private final NavigatorTree tree;
		private JDialog dialog;
		private GuiUtils guiUtils;

		public AllenCCFNavigator() {
			treeModel = AllenUtils.getTreeModel();
			tree = new NavigatorTree(treeModel);
			tree.setVisibleRowCount(10);
			tree.setEditable(false);
			tree.getCheckBoxTreeSelectionModel().setDigIn(false);
			tree.setExpandsSelectedPaths(true);
			tree.setRootVisible(true);
			GuiUtils.expandAllTreeNodes(tree);
			tree.setClickInCheckBoxOnly(false);
			searchableBar = new SNTSearchableBar(new TreeSearchable(tree));
			searchableBar.setStatusLabelPlaceholder("Common Coordinate Framework v"+ AllenUtils.VERSION);
			searchableBar.setVisibleButtons(
				SearchableBar.SHOW_NAVIGATION | SearchableBar.SHOW_HIGHLIGHTS |
				SearchableBar.SHOW_MATCHCASE | SearchableBar.SHOW_STATUS);
			refreshTree(false);
		}

		private List<AllenCompartment> getCheckedSelection() {
			final TreePath[] treePaths = tree.getCheckBoxTreeSelectionModel().getSelectionPaths();
			if (treePaths == null || treePaths.length == 0) {
				guiUtils.error("There are no checked ontologies.");
				return null;
			}
			final List<AllenCompartment> list = new ArrayList<>(treePaths.length);
			for (final TreePath treePath : treePaths) {
				final DefaultMutableTreeNode selectedElement = (DefaultMutableTreeNode) treePath.getLastPathComponent();
				list.add((AllenCompartment) selectedElement.getUserObject());
			}
			return list;
		}

		private void refreshTree(final boolean repaint) {
			for (final String meshLabel : getOBJs().keySet())
				meshLoaded(meshLabel);
			if (repaint)
				tree.repaint();
		}

		private void meshLoaded(final String meshLabel) {
			setCheckboxSelected(meshLabel, true);
			setCheckboxEnabled(meshLabel);
		}

		private void meshRemoved(final String meshLabel) {
			setCheckboxSelected(meshLabel, false);
			setCheckboxEnabled(meshLabel);
		}

		private void setCheckboxEnabled(final String nodeLabel) {
			final DefaultMutableTreeNode node = getNode(nodeLabel);
			if (node == null)
				return;
			tree.isCheckBoxEnabled(new TreePath(node.getPath()));
		}

		private void setCheckboxSelected(final String nodeLabel, final boolean enable) {
			final DefaultMutableTreeNode node = getNode(nodeLabel);
			if (node == null)
				return;
			if (enable)
				tree.getCheckBoxTreeSelectionModel().addSelectionPath(new TreePath(node.getPath()));
			else
				tree.getCheckBoxTreeSelectionModel().removeSelectionPath(new TreePath(node.getPath()));
		}

		private DefaultMutableTreeNode getNode(final String nodeLabel) {
			@SuppressWarnings("unchecked")
			final Enumeration<DefaultMutableTreeNode> e = ((DefaultMutableTreeNode) tree.getModel().getRoot())
					.depthFirstEnumeration();
			while (e.hasMoreElements()) {
				final DefaultMutableTreeNode node = e.nextElement();
				final AllenCompartment compartment = (AllenCompartment) node.getUserObject();
				if (nodeLabel.equals(compartment.name())) {
					return node;
				}
			}
			return null;
		}

		private void downloadMeshes() {
			final List<AllenCompartment> compartments = getCheckedSelection();
			if (compartments == null)
				return;
			int loadedCompartments = 0;
			final ArrayList<String> failedCompartments = new ArrayList<>();
			for (final AllenCompartment compartment : compartments) {
				if (getOBJs().keySet().contains(compartment.name())) {
					managerList.addCheckBoxListSelectedValue(compartment.name(), true);
				} else
					try {
						final OBJMesh msh = loadOBJMesh(compartment.getMesh());
						if (msh != null) {
							meshLoaded(compartment.name());
							loadedCompartments++;
						}
					} catch (final IllegalArgumentException ignored) {
						failedCompartments.add(compartment.name());
						meshRemoved(compartment.name());
					}
			}
			if (loadedCompartments > 0)
				getOuter().validate();
			if (failedCompartments.size() > 0) {
				final StringBuilder sb = new StringBuilder(String.valueOf(loadedCompartments)).append("/")
						.append(loadedCompartments + failedCompartments.size())
						.append(" meshes retrieved. The following compartments failed to load:").append("<br>&nbsp;<br>")
						.append(String.join("; ", failedCompartments)).append("<br>&nbsp;<br>")
						.append("Either such meshes are not available or file(s) could not be reached. Check Console logs for details.");
				guiUtils.centeredMsg(sb.toString(), "Exceptions Occured");
			}
		}

		private void showSelectionInfo() {
			final List<AllenCompartment> cs = getCheckedSelection();
			if (cs == null) return;
			System.out.println("*** Allen CCF Navigator: Info on selected items:");
			for (final AllenCompartment c : cs) {
				final StringBuilder sb = new StringBuilder();
				sb.append("   name[").append(c.name()).append("]");
				sb.append(" acronym[").append(c.acronym()).append("]");
				sb.append(" id[").append(c.id()).append("]");
				sb.append(" aliases[").append(String.join(",", c.aliases())).append("]");
				//sb.append(" UUID[").append(c.getUUID()).append("]");
				System.out.println(sb.toString());
			}
		}

		private JDialog show() {
			dialog = new JDialog(frame, "Allen CCF Ontology");
			frame.allenNavigator = this;
			guiUtils = new GuiUtils(dialog);
			dialog.setLocationRelativeTo(frame);
			dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			dialog.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(final WindowEvent e) {
					frame.allenNavigator = null;
					dialog.dispose();
				}
			});
			dialog.setContentPane(getContentPane());
			dialog.pack();
			dialog.setSize(new Dimension(searchableBar.getWidth(), dialog.getHeight()));
			dialog.setVisible(true);
			return dialog;
		}

		private JPanel getContentPane() {
			final JPanel barPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			frame.managerPanel.setFixedHeightToPanel(barPanel);
			barPanel.add(searchableBar);
			final JScrollPane scrollPane = new JScrollPane(tree);
			tree.setComponentPopupMenu(popupMenu());
			scrollPane.setWheelScrollingEnabled(true);
			final JPanel contentPane = new JPanel();
			contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
			contentPane.add(barPanel);
			contentPane.add(scrollPane);
			contentPane.add(buttonPanel());
			return contentPane;
		}

		private JPanel buttonPanel() {
			final JPanel buttonPanel = new JPanel(new GridLayout(1,2));
			buttonPanel.setBorder(null);
			frame.managerPanel.setFixedHeightToPanel(buttonPanel);
			JButton button = new JButton(IconFactory.getButtonIcon(GLYPH.INFO));
			button.addActionListener(e -> showSelectionInfo());
			buttonPanel.add(button);
			button = new JButton(IconFactory.getButtonIcon(GLYPH.IMPORT));
			button.addActionListener(e -> {
				downloadMeshes();
			});
			buttonPanel.add(button);
			return buttonPanel;
		}

		private JPopupMenu popupMenu() {
			final JPopupMenu pMenu = new JPopupMenu();
			JMenuItem jmi = new JMenuItem("Clear Selection");
			jmi.addActionListener(e -> {
				tree.clearSelection();
			});
			pMenu.add(jmi);
			jmi = new JMenuItem("Collapse All");
			jmi.addActionListener(e -> {
				GuiUtils.collapseAllTreeNodes(tree);
			});
			pMenu.add(jmi);
			jmi = new JMenuItem("Expand All");
			jmi.addActionListener(e -> GuiUtils.expandAllTreeNodes(tree));
			pMenu.add(jmi);
			return pMenu;
		}
		private class NavigatorTree extends CheckBoxTree {
			private static final long serialVersionUID = 1L;

			public NavigatorTree(final DefaultTreeModel treeModel) {
				super(treeModel);
			}

			@Override
			public boolean isCheckBoxEnabled(final TreePath treePath) {
				final DefaultMutableTreeNode selectedElement = (DefaultMutableTreeNode) treePath.getLastPathComponent();
				final AllenCompartment compartment = (AllenCompartment) selectedElement.getUserObject();
				return !getOBJs().containsKey(compartment.name());
			}
		}
	}

	private Viewer3D getOuter() {
		return this;
	}

	/**
	 * Closes and releases all the resources used by this viewer.
	 */
	public void dispose() {
		if (isSNTInstance()) sntService.getUI().setReconstructionViewer(null);
		frame.disposeFrame();
	}

	private class ShapeTree extends Shape {

		private static final float SOMA_SCALING_FACTOR = 2.5f;
		private static final float SOMA_SLICES = 15f; // Sphere default;

		private final Tree tree;
		private Shape treeSubShape;
		private AbstractWireframeable somaSubShape;
		private Coord3d translationReset;

		public ShapeTree(final Tree tree) {
			super();
			this.tree = tree;
			translationReset = new Coord3d(0f,0f,0f);
		}

		@Override
		public boolean isDisplayed() {
			return (super.isDisplayed()) || ((somaSubShape != null) && somaSubShape
					.isDisplayed()) || ((treeSubShape != null) && treeSubShape
					.isDisplayed());
		}

		@Override
		public void setDisplayed(final boolean displayed) {
			setArborDisplayed(displayed);
			// setSomaDisplayed(displayed);
		}

		public void setSomaDisplayed(final boolean displayed) {
			if (somaSubShape != null) somaSubShape.setDisplayed(displayed);
		}

		public void setArborDisplayed(final boolean displayed) {
			if (treeSubShape != null) treeSubShape.setDisplayed(displayed);
		}

		public Shape get() {
			if (components == null || components.isEmpty()) assembleShape();
			return this;
		}

		public void translateTo(final Coord3d destination) {
			final Transform tTransform = new Transform(new Translate(destination));
			get().applyGeometryTransform(tTransform);
			translationReset.subSelf(destination);
		}

		public void resetTranslation() {
			translateTo(translationReset);
			translationReset = new Coord3d(0f, 0f, 0f);
		}

		private void assembleShape() {

			final List<LineStrip> lines = new ArrayList<>();
			final List<PointInImage> somaPoints = new ArrayList<>();
			final List<java.awt.Color> somaColors = new ArrayList<>();

			for (final Path p : tree.list()) {

				// Stash soma coordinates
				if (Path.SWC_SOMA == p.getSWCType()) {
					for (int i = 0; i < p.size(); i++) {
						final PointInImage pim = p.getNode(i);
						pim.v = p.getNodeRadius(i);
						somaPoints.add(pim);
					}
					if (p.hasNodeColors()) {
						somaColors.addAll(Arrays.asList(p.getNodeColors()));
					}
					else {
						somaColors.add(p.getColor());
					}
					continue;
				}

				// Assemble arbor(s)
				final LineStrip line = new LineStrip(p.size());
				for (int i = 0; i < p.size(); ++i) {
					final PointInImage pim = p.getNode(i);
					final Coord3d coord = new Coord3d(pim.x, pim.y, pim.z);
					final Color color = fromAWTColor(p.hasNodeColors() ? p.getNodeColor(i)
							: p.getColor());
					final float width = Math.max((float) p.getNodeRadius(i),
							DEF_NODE_RADIUS);
					line.add(new Point(coord, color, width));
				}
				line.setShowPoints(true);
				line.setWireframeWidth(defThickness);
				lines.add(line);
			}

			// Group all lines into a Composite. BY default the composite
			// will have no wireframe color, to allow colors for Paths/
			// nodes to be revealed. Once a wireframe color is explicit
			// set it will be applied to all the paths in the composite
			if (!lines.isEmpty()) {
				treeSubShape = new Shape();
				treeSubShape.setWireframeColor(null);
				treeSubShape.add(lines);
				add(treeSubShape);
			}
			assembleSoma(somaPoints, somaColors);
			if (somaSubShape != null) add(somaSubShape);
			// shape.setFaceDisplayed(true);
			// shape.setWireframeDisplayed(true);
		}

		private void assembleSoma(final List<PointInImage> somaPoints,
								  final List<java.awt.Color> somaColors)
		{
			final Color color = fromAWTColor(SNTColor.average(somaColors));
			switch (somaPoints.size()) {
				case 0:
					//SNT.log(tree.getLabel() + ": No soma attribute");
					somaSubShape = null;
					return;
				case 1:
					// single point soma: http://neuromorpho.org/SomaFormat.html
					final PointInImage sCenter = somaPoints.get(0);
					somaSubShape = sphere(sCenter, color);
					return;
				case 3:
					// 3 point soma representation: http://neuromorpho.org/SomaFormat.html
					final PointInImage p1 = somaPoints.get(0);
					final PointInImage p2 = somaPoints.get(1);
					final PointInImage p3 = somaPoints.get(2);
					final Tube t1 = tube(p2, p1, color);
					final Tube t2 = tube(p1, p3, color);
					final Shape composite = new Shape();
					composite.add(t1);
					composite.add(t2);
					somaSubShape = composite;
					return;
				default:
					// just create a centroid sphere
					final PointInImage cCenter = PointInImage.average(somaPoints);
					somaSubShape = sphere(cCenter, color);
					return;
			}
		}

		private <T extends AbstractWireframeable & ISingleColorable> void
		setWireFrame(final T t, final float r, final Color color)
		{
			t.setColor(Utils.contrastColor(color).alphaSelf(0.4f));
			t.setWireframeColor(color.alphaSelf(0.8f));
			t.setWireframeWidth(Math.max(1f, r / SOMA_SLICES / 3));
			t.setWireframeDisplayed(true);
		}

		private Tube tube(final PointInImage bottom, final PointInImage top,
						  final Color color)
		{
			final Tube tube = new Tube();
			tube.setPosition(new Coord3d((bottom.x + top.x) / 2, (bottom.y + top.y) /
					2, (bottom.z + top.z) / 2));
			final float height = (float) bottom.distanceTo(top);
			tube.setVolume((float) bottom.v, (float) top.v, height);
			return tube;
		}

		private Sphere sphere(final PointInImage center, final Color color) {
			final Sphere s = new Sphere();
			s.setPosition(new Coord3d(center.x, center.y, center.z));
			final float radius = (float) Math.max(center.v, SOMA_SCALING_FACTOR *
					defThickness);
			s.setVolume(radius);
			setWireFrame(s, radius, color);
			return s;
		}

		public void rebuildShape() {
			if (isDisplayed()) {
				clear();
				assembleShape();
			}
		}

		public void setThickness(final float thickness) {
			treeSubShape.setWireframeWidth(thickness);
		}

		private void setArborColor(final ColorRGB color) {
			setArborColor(fromColorRGB(color));
		}

		private void setArborColor(final Color color) {
			treeSubShape.setWireframeColor(color);
//			for (int i = 0; i < treeSubShape.size(); i++) {
//				((LineStrip) treeSubShape.get(i)).setColor(color);
//			}
		}

		private Color getArborWireFrameColor() {
			return (treeSubShape == null) ? null : treeSubShape.getWireframeColor();
		}

		private Color getSomaColor() {
			return (somaSubShape == null) ? null : somaSubShape.getWireframeColor();
		}

		private void setSomaColor(final Color color) {
			if (somaSubShape != null) somaSubShape.setWireframeColor(color);
		}

		public void setSomaColor(final ColorRGB color) {
			setSomaColor(fromColorRGB(color));
		}

		public double[] colorize(final String measurement,
								 final ColorTable colorTable)
		{
			final TreeColorMapper colorizer = new TreeColorMapper();
			colorizer.map(tree, measurement, colorTable);
			rebuildShape();
			return colorizer.getMinMax();
		}

	}

	private static class Utils {

		private static Color contrastColor(final Color color) {
			final float factor = 0.75f;
			return new Color(factor - color.r, factor - color.g, factor - color.b);
		}
	}

	private class CmdWorker extends SwingWorker<Boolean, Object> {

		private static final int DO_NOTHING = 0;
		private static final int VALIDATE_SCENE = 1;
		private static final int RELOAD_PREFS = 2;


		private final Class<? extends Command> cmd;
		private final Map<String, Object> inputs;
		private final int type;
		private final boolean setRecViewerParamater;

		public CmdWorker(final Class<? extends Command> cmd,
			final Map<String, Object> inputs, final int type,
			final boolean setRecViewerParamater)
		{
			this.cmd = cmd;
			this.inputs = inputs;
			this.type = type;
			this.setRecViewerParamater = setRecViewerParamater;
		}

		@Override
		public Boolean doInBackground() {
			try {
				final Map<String, Object> input = new HashMap<>();
				if (setRecViewerParamater) input.put("recViewer", getOuter());
				if (inputs != null) input.putAll(inputs);
				cmdService.run(cmd, true, input).get();
				return true;
			}
			catch (final NullPointerException e1) {
				return false;
			}
			catch (InterruptedException | ExecutionException e2) {
				gUtils.error(
					"Unfortunately an exception occured. See console for details.");
				SNT.error("Error", e2);
				return false;
			}
		}

		@Override
		protected void done() {
			boolean status = false;
			try {
				status = get();
				if (status) {
					switch (type) {
						case VALIDATE_SCENE:
							validate();
							break;
						case RELOAD_PREFS:
							prefs.setPreferences();
						case DO_NOTHING:
						default:
							break;
					}
				}
			}
			catch (final Exception ignored) {
				// do nothing
			}
		}
	}

	private class MouseController extends AWTCameraMouseController {

		private float panStep = Prefs.PAN.MEDIUM.step;
		private boolean panDone;
		private Coord3d prevMouse3d;

		public MouseController(final Chart chart) {
			super(chart);
		}

		private int getY(final MouseEvent e) {
			return -e.getY() + chart.getCanvas().getRendererHeight();
		}

		private boolean recordRotation(final double endAngle, final int nSteps,
			final File dir)
		{

			if (!dir.exists()) dir.mkdirs();
			final double inc = Math.toRadians(endAngle) / nSteps;
			int step = 0;
			boolean status = true;

			// Make canvas dimensions divisible by 2 as most video encoders will
			// request it. Also, do not allow it to resize during the recording
			if (frame != null) {
				final int w = frame.canvas.getWidth() - (frame.canvas.getWidth() % 2);
				final int h = frame.canvas.getHeight() - (frame.canvas.getHeight() % 2);
				frame.canvas.setSize(w, h);
				frame.setResizable(false);
			}

			while (step++ < nSteps) {
				try {
					final File f = new File(dir, String.format("%05d.png", step));
					rotate(new Coord2d(inc, 0d), false);
					chart.screenshot(f);
				}
				catch (final IOException e) {
					status = false;
				}
				finally {
					if (frame != null) frame.setResizable(true);
				}
			}
			return status;
		}

		@Override
		public boolean handleSlaveThread(final MouseEvent e) {
			if (AWTMouseUtilities.isDoubleClick(e)) {
				if (currentView == ViewMode.TOP) {
					displayMsg("Rotation disabled in constrained view");
					return true;
				}
				if (threadController != null) {
					threadController.start();
					return true;
				}
			}
			if (threadController != null) threadController.stop();
			return false;
		}

		private void rotateLive(final Coord2d move) {
			if (currentView == ViewMode.TOP) {
				displayMsg("Rotation disabled in constrained view");
				return;
			}
			rotate(move, true);
		}

		@Override
		protected void rotate(final Coord2d move, final boolean updateView){
			// make method visible
			super.rotate(move, updateView);
		}

		/* see AWTMousePickingPan2dController */
		public void pan(final Coord3d from, final Coord3d to) {
			final BoundingBox3d viewBounds = view.getBounds();
			final Coord3d offset = to.sub(from).div(-panStep);
			final BoundingBox3d newBounds = viewBounds.shift(offset);
			((AView)view).setBoundManual(newBounds, true);
			view.shoot();
			fireControllerEvent(ControllerType.PAN, offset);
		}

		public void zoom(final float factor) {
			final BoundingBox3d viewBounds = view.getBounds();
			BoundingBox3d newBounds = viewBounds.scale(new Coord3d(factor, factor,
				factor));
			newBounds = newBounds.shift((viewBounds.getCenter().sub(newBounds
				.getCenter())));
			((AView) view).setBoundManual(newBounds, true);
			view.shoot();
			fireControllerEvent(ControllerType.ZOOM, factor);
		}

		public void snapToNextView() {
			stopThreadController();
			((AChart)chart).setViewMode(currentView.next());
			displayMsg("View Mode: " + currentView.description);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mousePressed(java.awt.event.MouseEvent)
		 */
		@Override
		public void mousePressed(final MouseEvent e) {
			if (e.isControlDown() && AWTMouseUtilities.isLeftDown(e) && !e.isConsumed()) {
				snapToNextView();
				e.consume();
				return;
			}
			super.mousePressed(e);
			prevMouse3d = view.projectMouse(e.getX(), getY(e));
		}
	
		/*
		 * (non-Javadoc)
		 *
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mouseWheelMoved(java.awt.event.MouseWheelEvent)
		 */
		@Override
		public void mouseWheelMoved(final MouseWheelEvent e) {
			stopThreadController();
			final float factor = 1 + e.getWheelRotation() * keyController.zoomStep;
			zoom(factor);
			prevMouse3d = view.projectMouse(e.getX(), getY(e));
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mouseDragged(java.awt.event.MouseEvent)
		 */
		@Override
		public void mouseDragged(final MouseEvent e) {

			final Coord2d mouse = xy(e);

			// Rotate on left-click
			if (AWTMouseUtilities.isLeftDown(e)) {
				final Coord2d move = mouse.sub(prevMouse).div(100);
				rotate(move);
				//((AView)view).logViewPoint();
			}

			// Pan on right-click
			else if (AWTMouseUtilities.isRightDown(e)) {
				final Coord3d thisMouse3d = view.projectMouse(e.getX(), getY(e));
				if (!panDone) { // 1/2 pan for cleaner rendering
					pan(prevMouse3d, thisMouse3d);
					panDone = true;
				}
				else {
					panDone = false;
				}
				prevMouse3d = thisMouse3d;
			}
			prevMouse = mouse;
		}
	}

	private class KeyController extends AbstractCameraController implements
		KeyListener
	{

		private float zoomStep;
		private double rotationStep;

		public KeyController(final Chart chart) {
			register(chart);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyPressed(final KeyEvent e) {
			switch (e.getKeyChar()) {
				case 'a':
				case 'A':
					toggleAxes();
					break;
				case 'c':
				case 'C':
					changeCameraMode();
					break;
				case 'd':
				case 'D':
					toggleDarkMode();
					break;
				case 'h':
				case 'H':
					showHelp(false);
					break;
				case 'r':
				case 'R':
					resetView();
					break;
				case 's':
				case 'S':
					saveScreenshot();
					break;
				case 'f':
				case 'F':
					fitToVisibleObjects(true);
					break;
				case '+':
				case '=':
					mouseController.zoom(1f - zoomStep);
					break;
				case '-':
				case '_':
					mouseController.zoom(1f + zoomStep);
					break;
				default:
					switch (e.getKeyCode()) {
						case KeyEvent.VK_F1:
							showHelp(true);
							break;
						case KeyEvent.VK_DOWN:
							mouseController.rotateLive(new Coord2d(0f, -rotationStep));
							break;
						case KeyEvent.VK_UP:
							mouseController.rotateLive(new Coord2d(0f, rotationStep));
							break;
						case KeyEvent.VK_LEFT:
							mouseController.rotateLive(new Coord2d(-rotationStep, 0));
							break;
						case KeyEvent.VK_RIGHT:
							mouseController.rotateLive(new Coord2d(rotationStep, 0));
							break;
						default:
							break;
					}
			}
		}

		private void toggleAxes() {
			chart.setAxeDisplayed(!view.isAxeBoxDisplayed());
		}

		@SuppressWarnings("unused")
		private boolean emptyScene() {
			final boolean empty = view.getScene().getGraph().getAll().size() == 0;
			if (empty) displayMsg("Scene is empty");
			return empty;
		}

		private void saveScreenshot() {
			getOuter().saveSnapshot();
			displayMsg("Snapshot saved to " + FileUtils.limitPath(
				prefs.snapshotDir, 50));
		}

		private void resetView() {
			try {
				chart.setViewPoint(View.DEFAULT_VIEW);
				chart.setViewMode(ViewPositionMode.FREE);
				view.setBoundMode(ViewBoundMode.AUTO_FIT);
				displayMsg("View reset");
			} catch (final GLException ex) {
				SNT.error("Is scene empty? ", ex);
			}
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyTyped(final KeyEvent e) {}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyReleased(final KeyEvent e) {}

		private void changeCameraMode() {
			final CameraMode newMode = (view.getCameraMode() == CameraMode.ORTHOGONAL)
				? CameraMode.PERSPECTIVE : CameraMode.ORTHOGONAL;
			view.setCameraMode(newMode);
			final String mode = (newMode == CameraMode.ORTHOGONAL) ? "Orthogonal"
				: "Perspective";
			displayMsg("Camera mode changed to \"" + mode + "\"");
		}

		/* This seems to work only at initialization */
		@SuppressWarnings("unused")
		private void changeQuality() {
			final Quality[] levels = { Quality.Fastest, Quality.Intermediate,
				Quality.Advanced, Quality.Nicest };
			final String[] grades = { "Fastest", "Intermediate", "High", "Best" };
			final Quality currentLevel = chart.getQuality();
			int nextLevelIdx = 0;
			for (int i = 0; i < levels.length; i++) {
				if (levels[i] == currentLevel) {
					nextLevelIdx = i + 1;
					break;
				}
			}
			if (nextLevelIdx == levels.length) nextLevelIdx = 0;
			chart.setQuality(levels[nextLevelIdx]);
			displayMsg("Quality level changed to '" + grades[nextLevelIdx] + "'");
		}

		private void toggleDarkMode() {
//			if (chart == null)
//				return;
			Color newForeground;
			Color newBackground;
			if (view.getBackgroundColor() == Color.BLACK) {
				newForeground = Color.BLACK;
				newBackground = Color.WHITE;
			}
			else {
				newForeground = Color.WHITE;
				newBackground = Color.BLACK;
			}
			view.setBackgroundColor(newBackground);
			view.getAxe().getLayout().setGridColor(newForeground);
			view.getAxe().getLayout().setMainColor(newForeground);
			((AChart)chart).overlayAnnotation.setForegroundColor(newForeground);
			if (cBar != null) cBar.updateColors();

			// Apply foreground color to trees with background color
			plottedTrees.values().forEach(shapeTree -> {
				if (isSameRGB(shapeTree.getSomaColor(), newBackground)) shapeTree
					.setSomaColor(newForeground);
				if (isSameRGB(shapeTree.getArborWireFrameColor(), newBackground)) {
					shapeTree.setArborColor(newForeground);
					return; // replaces continue in lambda expression;
				}
				final Shape shape = shapeTree.treeSubShape;
				for (int i = 0; i < shapeTree.treeSubShape.size(); i++) {
					final List<Point> points = ((LineStrip) shape.get(i)).getPoints();
					points.stream().forEach(p -> {
						final Color pColor = p.getColor();
						if (isSameRGB(pColor, newBackground)) {
							changeRGB(pColor, newForeground);
						}
					});
				}
			});

			// Apply foreground color to meshes with background color
			plottedObjs.values().forEach(obj -> {
				final Color objColor = obj.getColor();
				if (isSameRGB(objColor, newBackground)) {
					changeRGB(objColor, newForeground);
				}
			});

		}

		private boolean isSameRGB(final Color c1, final Color c2) {
			return c1 != null && c1.r == c2.r && c1.g == c2.g && c1.b == c2.b;
		}

		private void changeRGB(final Color from, final Color to) {
			from.r = to.r;
			from.g = to.g;
			from.b = to.b;
		}

		private void showHelp(final boolean showInDialog) {
			final StringBuilder sb = new StringBuilder("<HTML>");
			sb.append("<table>");
			sb.append("  <tr>");
			sb.append("    <td>Pan</td>");
			sb.append("    <td>Right-click &amp; drag</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Rotate</td>");
			sb.append("    <td>Left-click &amp; drag (or arrow keys)</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Zoom (Scale)</td>");
			sb.append("    <td>Scroll (or + / - keys)</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Animate</td>");
			sb.append("    <td>Double left-click</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Snap to Top/Side View</td>");
			sb.append("    <td>Ctrl + left-click</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>A</u>xes</td>");
			sb.append("    <td>Press 'A'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>C</u>amera Mode</td>");
			sb.append("    <td>Press 'C'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>D</u>ark Mode</td>");
			sb.append("    <td>Press 'D'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>F</u>it View to Visible Objects &nbsp;</td>");
			sb.append("    <td>Press 'F'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>R</u>eset View</td>");
			sb.append("    <td>Press 'R'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>S</u>napshot</td>");
			sb.append("    <td>Press 'S'</td>");
			sb.append("  </tr>");
			if (showInDialog) {
				sb.append("  <tr>");
				sb.append("    <td><u>H</u>elp</td>");
				sb.append("    <td>Press 'H' (notification) or F1 (list)</td>");
				sb.append("  </tr>");
			}
			sb.append("</table>");
			if (showInDialog) {
				new HTMLDialog("Reconstruction Viewer Shortcuts", sb.toString(), false);
			}
			else {
				displayMsg(sb.toString(), 10000);
			}

		}
	}

	private class OverlayAnnotation extends CameraEyeOverlayAnnotation {

		private final GLAnimatorControl control;
		private java.awt.Color color;
		private String label;
		private Font labelFont;
		private java.awt.Color labelColor;
		private float labelX = 2;
		private float labelY = 0;

		public OverlayAnnotation(final View view) {
			super(view);
			control = ((IScreenCanvas) view.getCanvas()).getAnimator();
			control.setUpdateFPSFrames(FPSCounter.DEFAULT_FRAMES_PER_INTERVAL, null);
		}

		private void setForegroundColor(final Color c) {
			color = new java.awt.Color(c.r, c.g, c.b);
		}

		public void setFont(final Font font, final float angle) {
			if (angle == 0) {
				this.labelFont = font;
				return;
			}
			final AffineTransform affineTransform = new AffineTransform();
			affineTransform.rotate(Math.toRadians(angle), 0, 0);
			labelFont = font.deriveFont(affineTransform);
		}

		public void setLabelColor(final java.awt.Color labelColor) {
			this.labelColor = labelColor;
		}

		@Override
		public void paint(final Graphics g, final int canvasWidth,
			final int canvasHeight)
		{
			final Graphics2D g2d = (Graphics2D) g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.setColor(color);
			if (SNT.isDebugMode()) {
				int lineHeight = g.getFontMetrics().getHeight();
				g2d.drawString("Camera: " + view.getCamera().getEye(), 20, lineHeight);
				g2d.drawString("FOV: " + view.getCamera().getRenderingSphereRadius(),
					20, lineHeight += lineHeight);
				g2d.drawString(control.getLastFPS() + " FPS", 20, lineHeight +=
					lineHeight);
			}
			if (label == null || label.isEmpty()) return;
			if (labelColor != null) g2d.setColor(labelColor);
			if (labelFont != null) g2d.setFont(labelFont);
			final int lineHeight = g2d.getFontMetrics().getHeight();
			float ypos = labelY; //(labelY < lineHeight) ? lineHeight : labelY;
			for (final String line : label.split("\n")) {
				g2d.drawString(line, labelX, ypos += lineHeight);
			}
		}
	}

	private String getLabel(final OBJMesh mesh) {
		for (final Entry<String, RemountableDrawableVBO> entry : plottedObjs.entrySet()) {
			if (entry.getValue().objMesh == mesh) return entry.getKey();
		}
		return null;
	}

	private String getLabel(final Tree tree) {
		for (final Map.Entry<String, ShapeTree> entry : plottedTrees.entrySet()) {
			if (entry.getValue().tree == tree) return entry.getKey();
		}
		return null;
	}

	/**
	 * Sets the line thickness for rendering {@link Tree}s that have no specified
	 * radius.
	 *
	 * @param thickness the new line thickness. Note that this value only applies
	 *          to Paths that have no specified radius
	 */
	public void setDefaultThickness(final float thickness) {
		this.defThickness = thickness;
	}

	private synchronized void fitToVisibleObjects(final boolean beGreedy)
		throws NullPointerException
	{
		final List<AbstractDrawable> all = chart.getView().getScene().getGraph()
			.getAll();
		final BoundingBox3d bounds = new BoundingBox3d(0, 0, 0, 0, 0, 0);
		all.stream().forEach(d -> {
			if (d != null && d.isDisplayed() && d.getBounds() != null && !d
				.getBounds().isReset())
			{
				bounds.add(d.getBounds());
			}
		});
		if (bounds.isPoint()) return;
		if (beGreedy) {
			BoundingBox3d zoomedBox = bounds.scale(new Coord3d(.85f, .85f, .85f));
			zoomedBox = zoomedBox.shift((bounds.getCenter().sub(zoomedBox.getCenter())));
			chart.view().lookToBox(zoomedBox);
		}
		else {
			chart.view().lookToBox(bounds);
		}
	}

	/**
	 * Sets the default color for rendering {@link Tree}s.
	 *
	 * @param color the new color. Note that this value only applies to Paths that
	 *          have no specified color and no colors assigned to its nodes
	 */
	public void setDefaultColor(final ColorRGB color) {
		this.defColor = fromColorRGB(color);
	}

	/**
	 * Returns the default line thickness.
	 *
	 * @return the default line thickness used to render Paths without radius
	 */
	private float getDefaultThickness() {
		return defThickness;
	}

	/**
	 * Applies a constant thickness (line width) to a subset of rendered trees.
	 *
	 * @param labels the Collection of keys specifying the subset of trees
	 * @param thickness the thickness (line width)
	 * @see #setTreesThickness(float)
	 */
	public void setTreesThickness(final Collection<String> labels,
		final float thickness)
	{
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) shapeTree.setThickness(thickness);
		});
	}

	/**
	 * Applies a constant thickness to all rendered trees. Note that by default,
	 * trees are rendered using their nodes' diameter.
	 *
	 * @param thickness the thickness (line width)
	 * @see #setTreesThickness(Collection, float)
	 */
	public void setTreesThickness(final float thickness) {
		plottedTrees.values().forEach(shapeTree -> shapeTree.setThickness(
			thickness));
	}

	/**
	 * Translates the specified Tree. Does nothing if tree is not present in scene.
	 *
	 * @param tree the Tree to be translated
	 * @param offset the translation offset. If null, tree position will be reset to
	 *               their original location.
	 */
	public void translate(final Tree tree, final SNTPoint offset){
		final String treeLabel = getLabel(tree);
		if (treeLabel != null) this.translate(Collections.singletonList(treeLabel), offset);
	}

	/**
	 * Translates the specified collection of {@link Tree}s.
	 *
	 * @param treeLabels the collection of Tree identifiers (as per
	 *          {@link #add(Tree)}) specifying the Trees to be translated
	 * @param offset the translation offset. If null, trees position will be reset
	 *          to their original location.
	 */
	public void translate(final Collection<String> treeLabels,
		final SNTPoint offset)
	{
		if (offset == null) {
			plottedTrees.forEach((k, shapeTree) -> {
				if (treeLabels.contains(k)) shapeTree.resetTranslation();
			});
		}
		else {
			final Coord3d coord = new Coord3d(offset.getX(), offset.getY(), offset
				.getZ());
			plottedTrees.forEach((k, shapeTree) -> {
				if (treeLabels.contains(k)) shapeTree.translateTo(coord);
			});
		}
		if (viewUpdatesEnabled) {
			view.shoot();
			fitToVisibleObjects(true);
		}
	}

	private void setArborsDisplayed(final Collection<String> labels,
		final boolean displayed)
	{
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) setArborDisplayed(k, displayed);
		});
	}

	private void setArborDisplayed(final String treeLabel,
		final boolean displayed)
	{
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree != null) shapeTree.setArborDisplayed(displayed);
	}

	/**
	 * Toggles the visibility of somas for subset of trees.
	 *
	 * @param labels the Collection of keys specifying the subset of trees to be
	 *          affected
	 * @param displayed whether soma should be displayed
	 */
	public void setSomasDisplayed(final Collection<String> labels,
		final boolean displayed)
	{
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) setSomaDisplayed(k, displayed);
		});
	}

	/**
	 * Toggles the visibility of somas for all trees in the scene.
	 *
	 * @param displayed whether soma should be displayed
	 */
	public void setSomasDisplayed(final boolean displayed) {
		plottedTrees.values().forEach(shapeTree -> shapeTree.setSomaDisplayed(displayed));
	}

	private void setSomaDisplayed(final String treeLabel,
		final boolean displayed)
	{
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree != null) shapeTree.setSomaDisplayed(displayed);
	}

	/**
	 * Applies a color to a subset of rendered trees.
	 *
	 * @param labels the Collection of keys specifying the subset of trees
	 * @param color the color
	 */
	private void applyColorToPlottedTrees(final List<String> labels,
		final ColorRGB color)
	{
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) {
				shapeTree.setArborColor(color);
				shapeTree.setSomaColor(color);
			}
		});
	}

	private boolean treesContainColoredNodes(final List<String> labels) {
		Color refColor = null;
		for (final Map.Entry<String, ShapeTree> entry : plottedTrees.entrySet()) {
			if (labels.contains(entry.getKey())) {
				final Shape shape = entry.getValue().treeSubShape;
				for (int i = 0; i < shape.size(); i++) {
					// treeSubShape is only composed of LineStrips so this is a safe
					// casting
					final Color color = getNodeColor((LineStrip) shape.get(i));
					if (color == null) continue;
					if (refColor == null) {
						refColor = color;
						continue;
					}
					if (color.r != refColor.r || color.g != refColor.g ||
						color.b != refColor.b) return true;
				}
			}
		}
		return false;
	}

	private Color getNodeColor(final LineStrip lineStrip) {
		for (final Point p : lineStrip.getPoints()) {
			if (p != null) return p.rgb;
		}
		return null;
	}

	/**
	 * Checks whether this instance is SNT's Reconstruction Viewer.
	 *
	 * @return true, if SNT instance, false otherwise
	 */
	public boolean isSNTInstance() {
		return sntService != null && sntService.isActive() && sntService
			.getUI() != null && this.equals(sntService.getUI()
				.getReconstructionViewer(false));
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (o == null) return false;
		if (getClass() != o.getClass()) return false;
		return uuid.equals(((Viewer3D) o).uuid);
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		//final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final Tree tree = new Tree("/home/kharrington/Dropbox/quickGitBackup/instar-superstar/data/pair/A02m_a3l Pseudolooper-3_406883.swc");
		final TreeColorMapper colorizer = new TreeColorMapper(ij.getContext());
		colorizer.map(tree, TreeColorMapper.BRANCH_ORDER, ColorTables.ICE);
		final double[] bounds = colorizer.getMinMax();
		SNT.setDebugMode(true);
		final Viewer3D jzy3D = new Viewer3D(ij.context());
		jzy3D.addColorBarLegend(ColorTables.ICE, (float) bounds[0],
			(float) bounds[1], new Font("Arial", Font.PLAIN, 24), 3, 4);
		jzy3D.add(tree);
		final OBJMesh brainMesh = jzy3D.loadMouseRefBrain();
		brainMesh.setBoundingBoxColor(Colors.RED);
		final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
		jzy3D.addSurface(analyzer.getTips());
		jzy3D.show();
		jzy3D.setAnimationEnabled(true);
		jzy3D.setViewPoint(-1.5707964f, -1.5707964f);
		jzy3D.updateColorBarLegend(-8, 88);
	}

}
