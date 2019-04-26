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

import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.json.JSONException;
import org.scijava.java3d.View;
import org.scijava.util.ColorRGB;
import org.scijava.vecmath.Color3f;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij3d.Content;
import ij3d.UniverseListener;
import tracing.gui.GuiUtils;
import tracing.io.MouseLightLoader;
import tracing.util.BoundingBox;
import tracing.util.PointInImage;
import tracing.util.SNTColor;
import tracing.util.SNTPoint;
import tracing.util.SWCPoint;
import util.Bresenham3D;
import util.XMLFunctions;

/**
 * The PathAndFillManager is responsible for importing, handling and managing of
 * Paths and Fills. Typically, a PathAndFillManager is accessed from an
 * {@link SimpleNeuriteTracer} instance, but accessing a PathAndFillManager
 * directly is useful for batch/headless operations.
 */
public class PathAndFillManager extends DefaultHandler implements
	UniverseListener
{

	protected static final int TRACES_FILE_TYPE_COMPRESSED_XML = 1;
	protected static final int TRACES_FILE_TYPE_UNCOMPRESSED_XML = 2;
	protected static final int TRACES_FILE_TYPE_SWC = 3;
	protected static final int TRACES_FILE_TYPE_ML_JSON = 4;

	private static final DecimalFormat fileIndexFormatter = new DecimalFormat(
		"000");

	protected SimpleNeuriteTracer plugin;
	private static boolean headless = false;
	private double x_spacing;
	private double y_spacing;
	private double z_spacing;
	private String spacing_units;
	/** BoundingBox for existing Paths */
	private BoundingBox boundingBox;
	protected boolean spacingIsUnset;

	private final ArrayList<Path> allPaths;
	private final ArrayList<Fill> allFills;
	private final ArrayList<PathAndFillListener> listeners;
	private final HashSet<Path> selectedPathsSet;
	private int maxUsedID = -1;

	private Fill current_fill;
	private Path current_path;
	private HashMap<Integer, Integer> startJoins;
	private HashMap<Integer, Integer> startJoinsIndices;
	private HashMap<Integer, PointInImage> startJoinsPoints;
	private HashMap<Integer, Integer> endJoins;
	private HashMap<Integer, Integer> endJoinsIndices;
	private HashMap<Integer, PointInImage> endJoinsPoints;
	private HashMap<Integer, Boolean> useFittedFields;
	private HashMap<Integer, Integer> fittedFields;
	private HashMap<Integer, Integer> fittedVersionOfFields;
	private ArrayList<int[]> sourcePathIDForFills;

	private int last_fill_node_id;
	private int last_fill_id;
	private HashSet<Integer> foundIDs;
	protected boolean enableUIupdates = true;

	/**
	 * Instantiates a new PathAndFillManager using default values. Voxel
	 * dimensions are inferred from the first imported set of nodes.
	 */
	public PathAndFillManager() {
		allPaths = new ArrayList<>();
		allFills = new ArrayList<>();
		listeners = new ArrayList<>();
		selectedPathsSet = new HashSet<>();
		boundingBox = new BoundingBox();
		x_spacing = boundingBox.xSpacing;
		y_spacing = boundingBox.ySpacing;
		z_spacing = boundingBox.zSpacing;
		spacing_units = boundingBox.getUnit();
		plugin = null;
		spacingIsUnset = true;
	}

	protected PathAndFillManager(final SimpleNeuriteTracer plugin) {
		this();
		this.plugin = plugin;
		syncPluginSpatialSettings();
		addPathAndFillListener(plugin);
	}

	/**
	 * Instantiates a new PathAndFillManager imposing specified pixel dimensions,
	 * which may be required for pixel operations. New {@link Path}s created under
	 * this instance, will adopt the specified spacing details.
	 *
	 * @param x_spacing the 'voxel width'
	 * @param y_spacing the 'voxel height'
	 * @param z_spacing the 'voxel depth'
	 * @param spacing_units the spacing units (e.g., 'um', 'mm)
	 */
	public PathAndFillManager(final double x_spacing, final double y_spacing,
		final double z_spacing, final String spacing_units)
	{
		this();
		boundingBox.setSpacing(x_spacing, y_spacing, z_spacing, spacing_units);
		boundingBox.setOrigin(new PointInImage(0, 0, 0));
		this.x_spacing = x_spacing;
		this.y_spacing = y_spacing;
		this.z_spacing = z_spacing;
		this.spacing_units = boundingBox.getUnit();
		spacingIsUnset = false;
	}

	protected void syncPluginSpatialSettings() {
		x_spacing = plugin.x_spacing;
		y_spacing = plugin.y_spacing;
		z_spacing = plugin.z_spacing;
		spacing_units = plugin.spacing_units;
		boundingBox.setOrigin(new PointInImage(0, 0, 0));
		boundingBox.setSpacing(x_spacing, y_spacing, z_spacing,
			spacing_units);
		boundingBox.setDimensions(plugin.width, plugin.height, plugin.depth);
		spacingIsUnset = false;
	}

	/**
	 * Returns the number of Paths in the PathAndFillManager list.
	 *
	 * @return the the number of Paths
	 */
	public int size() {
		return allPaths.size();
	}

	/**
	 * Adds a PathAndFillListener. This is used by the interface to have changes
	 * in the path manager reported so that they can be reflected in the UI.
	 *
	 * @param listener the listener
	 */
	public synchronized void addPathAndFillListener(
		final PathAndFillListener listener)
	{
		listeners.add(listener);
	}

	/**
	 * Returns the Path at the specified position in the PathAndFillManager list.
	 *
	 * @param i the index of the Path
	 * @return the Path at the specified index
	 */
	public synchronized Path getPath(final int i) {
		return allPaths.get(i);
	}

	/**
	 * Returns the Path with the specified name.
	 *
	 * @param name the name of the Path to be retrieved
	 * @param caseSensitive If true, case considerations are ignored
	 * @return the Path with the specified name, or null if name was not found.
	 */
	public synchronized Path getPathFromName(final String name,
		final boolean caseSensitive)
	{
		for (final Path p : allPaths) {
			if (caseSensitive) {
				if (name.equals(p.getName())) return p;
			}
			else {
				if (name.equalsIgnoreCase(p.getName())) return p;
			}
		}
		return null;
	}

	protected synchronized Path getPathFrom3DViewerName(final String name) {
		for (final Path p : allPaths) {
			if (p.nameWhenAddedToViewer == null) continue;
			if (name.equals(p.nameWhenAddedToViewer)) return p;
		}
		return null;
	}

	/**
	 * Returns the Path with the specified id.
	 *
	 * @param id the id of the Path to be retrieved
	 * @return the Path with the specified id, or null if id was not found.
	 */
	public synchronized Path getPathFromID(final int id) {
		for (final Path p : allPaths) {
			if (id == p.getID()) {
				return p;
			}
		}
		return null;
	}

	/*
	 * This is called to update the PathAndFillManager's idea of which paths are
	 * currently selected. This is also propagated to:
	 *
	 * (a) Each Path object (so that the 3D viewer can reflect the change, for
	 * instance.)
	 *
	 * (b) All the registered PathAndFillListener objects.
	 */
	protected synchronized void setSelected(final Collection<Path> selectedPaths,
		final Object sourceOfMessage)
	{
		selectedPathsSet.clear();
		if (selectedPaths != null) {
			// selectedPathsSet.addAll(selectedPaths);
			selectedPaths.forEach(p -> {
				Path pathToSelect = p;
				if (pathToSelect.getUseFitted()) pathToSelect = pathToSelect.fitted;
				// pathToSelect.setSelected(true);
				selectedPathsSet.add(pathToSelect);
			});
		}
		for (final PathAndFillListener pafl : listeners) {
			if (pafl != sourceOfMessage)
				// The source of the message already knows the states:
				pafl.setSelectedPaths(selectedPathsSet, this);
		}
		if (plugin != null) {
			plugin.updateAllViewers();
		}
	}

	/**
	 * Checks if a Path is currently selected in the GUI.
	 *
	 * @param path the path to be checked
	 * @return true, if is selected
	 */
	public synchronized boolean isSelected(final Path path) {
		return selectedPathsSet.contains(path);
	}

	/**
	 * Gets all paths selected in the GUI
	 *
	 * @return the collection of selected paths
	 */
	public Set<Path> getSelectedPaths() {
		return selectedPathsSet;
	}

	/**
	 * Checks whether at least one Path is currently selected in the UI.
	 *
	 * @return true, if successful
	 */
	public boolean anySelected() {
		return selectedPathsSet.size() > 0;
	}

	protected File getSWCFileForIndex(final String prefix, final int index) {
		return new File(prefix + "-" + fileIndexFormatter.format(index) + ".swc");
	}

	/**
	 * Exports all as Paths as SWC file(s). Multiple files are created if multiple
	 * Trees exist.
	 *
	 * @param prefix the common prefix to all exported files
	 * @return true, if successful
	 */
	public synchronized boolean exportAllPathsAsSWC(final String prefix) {
		final Path[] primaryPaths = getPathsStructured();
		int i = 0;
		for (final Path primaryPath : primaryPaths) {
			final File swcFile = getSWCFileForIndex(prefix, i);
			final HashSet<Path> connectedPaths = new HashSet<>();
			final LinkedList<Path> nextPathsToConsider = new LinkedList<>();
			nextPathsToConsider.add(primaryPath);
			while (nextPathsToConsider.size() > 0) {
				final Path currentPath = nextPathsToConsider.removeFirst();
				connectedPaths.add(currentPath);
				for (final Path joinedPath : currentPath.somehowJoins) {
					if (!connectedPaths.contains(joinedPath)) nextPathsToConsider.add(
						joinedPath);
				}
			}

			List<SWCPoint> swcPoints = null;
			try {
				swcPoints = getSWCFor(new ArrayList<>(connectedPaths));
			}
			catch (final SWCExportException see) {
				error("" + see.getMessage());
				return false;
			}

			if (plugin != null) plugin.showStatus(0, 0, "Exporting SWC data to " +
				swcFile.getAbsolutePath());

			try {
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(
					new FileOutputStream(swcFile), StandardCharsets.UTF_8));
				flushSWCPoints(swcPoints, pw);
			}
			catch (final IOException ioe) {
				error("Saving to " + swcFile.getAbsolutePath() + " failed.");
				SNT.error("IOException", ioe);
				return false;
			}
			++i;
		}
		if (plugin != null) plugin.showStatus(0, 0, "Export finished.");
		return true;
	}

	/**
	 * Sets whether this PathAndFillManager instance should run headless.
	 *
	 * @param headless true to activate headless calls, otherwise false
	 */
	public void setHeadless(final boolean headless) {
		PathAndFillManager.headless = headless;
	}

	private static void errorStatic(final String msg) {
		if (headless || GraphicsEnvironment.isHeadless()) {
			SNT.error(msg);
		}
		else {
			GuiUtils.errorPrompt(msg);
		}
	}

	private void error(final String msg) {
		if (!headless && plugin != null && plugin.isUIready()) plugin.error(msg);
		else errorStatic(msg);
	}

	protected void flushSWCPoints(final List<SWCPoint> swcPoints,
		final PrintWriter pw)
	{
		pw.println("# Exported from SNT v" +
			SNT.VERSION + " on " + LocalDateTime.of(LocalDate.now(), LocalTime
				.now()));
		pw.println("# https://imagej.net/SNT");
		pw.println("#");
		pw.println("# All positions and radii in " + spacing_units);
		if (usingNonPhysicalUnits()) pw.println(
			"# WARNING: Usage of pixel coordinates does not respect the SWC specification");
		else pw.println("# Voxel separation (x,y,z): " + x_spacing + ", " +
			y_spacing + ", " + z_spacing);
		pw.println("#");
		SWCPoint.flush(swcPoints, pw);
		pw.close();
	}

	protected boolean usingNonPhysicalUnits() {
		return (new Calibration().getUnits()).equals(spacing_units) || (SNT
			.getSanitizedUnit(null).equals(spacing_units) && x_spacing * y_spacing *
				z_spacing == 1d);
	}

	protected void updateBoundingBox() {
		boundingBox = getBoundingBox(true);
	}

	/**
	 * Returns the BoundingBox enclosing all nodes of all existing Paths.
	 *
	 * @param compute If true, BoundingBox dimensions will be computed for all the
	 *          existing Paths. If false, the last computed BoundingBox will be
	 *          returned. Also, if BoundingBox is not scaled, its spacing will be
	 *          computed from the smallest inter-node distance of an arbitrary '
	 *          large' Path. Computations of Path boundaries typically occur
	 *          during import operations.
	 * @return the BoundingBox enclosing existing nodes, or an 'empty' BoundingBox
	 *         if no computation of boundaries has not yet occurred. Output is
	 *         never null.
	 */
	public BoundingBox getBoundingBox(final boolean compute) {
		if (boundingBox == null) boundingBox = new BoundingBox();
		if (!compute || getPaths().size() == 0) return boundingBox;
		final Iterator<PointInImage> allPointsIt = allPointsIterator();
		boundingBox.compute(allPointsIt);
		return boundingBox;
	}

	/*
	 * This method returns an array of the "primary paths", which should be
	 * displayed at the top of a tree-like hierarchy.
	 *
	 * The paths actually form a graph, of course, but most UIs will want to display
	 * the graph as a tree.
	 */

	public synchronized Path[] getPathsStructured() {

		final ArrayList<Path> primaryPaths = new ArrayList<>();

		/*
		 * Some paths may be explicitly marked as primary, so extract those and
		 * everything connected to them first. If you encounter another path marked as
		 * primary when exploring from these then that's an error...
		 */

		final TreeSet<Path> pathsLeft = new TreeSet<>();

		for (final Path p : allPaths) {
			if (!p.isFittedVersionOfAnotherPath()) pathsLeft.add(p);
		}

		/*
		 * This is horrendously inefficent but with the number of paths that anyone
		 * might reasonably add by hand (I hope!) it's acceptable.
		 */

		Iterator<Path> pi = pathsLeft.iterator();
		Path primaryPath = null;
		while (pi.hasNext()) {
			final Path p = pi.next();
			if (p.isPrimary()) {
				pi.remove();
				primaryPaths.add(p);
			}
		}

		for (int i = 0; i < primaryPaths.size(); ++i) {
			primaryPath = primaryPaths.get(i);
			primaryPath.setChildren(pathsLeft);
		}

		// Start with each one left that doesn't start on another:
		boolean foundOne = true;
		while (foundOne) {
			foundOne = false;
			pi = pathsLeft.iterator();
			while (pi.hasNext()) {
				final Path p = pi.next();
				if (p.getStartJoins() == null) {
					foundOne = true;
					pi.remove();
					primaryPaths.add(p);
					p.setChildren(pathsLeft);
					break;
				}
			}
		}

		// If there's anything left, start with that:
		while (pathsLeft.size() > 0) {
			pi = pathsLeft.iterator();
			final Path p = pi.next();
			pi.remove();
			primaryPaths.add(p);
			p.setChildren(pathsLeft);
		}

		return primaryPaths.toArray(new Path[] {});
	}

	/**
	 * Gets the list of SWCPoints associated with a collection of Paths.
	 *
	 * @param paths the paths from which to retrieve the list
	 * @return the list of SWCPoints, corresponding to all the nodes of
	 *         {@code paths}
	 * @throws SWCExportException if list could not be retrieved
	 */
	public synchronized List<SWCPoint> getSWCFor(final Collection<Path> paths)
		throws SWCExportException
	{

		/*
		 * Turn the primary paths into a Set. This call also ensures that the
		 * Path.children and Path.somehowJoins relationships are set up correctly:
		 */
		final Set<Path> structuredPathSet = new HashSet<>(Arrays.asList(
			getPathsStructured()));

		/*
		 * Check that there's only one primary path in selectedPaths by taking the
		 * intersection and checking there's exactly one element in it:
		 */
		structuredPathSet.retainAll(new HashSet<>(paths));

		if (structuredPathSet.size() == 0) throw new SWCExportException(
			"The paths you select for SWC export must include a primary path (i.e., one at the top level in the Path Manager tree)");
		if (structuredPathSet.size() > 1) throw new SWCExportException(
			"You can only select one connected set of paths for SWC export");

		/*
		 * So now we definitely only have one primary path. All the connected paths must
		 * also be selected, but we'll check that as we go along:
		 */

		final ArrayList<SWCPoint> result = new ArrayList<>();

		int currentPointID = 1;

		/*
		 * nextPathsToAdd is the queue of Paths to add points from, and pathsAlreadyDone
		 * is the set of Paths that have already had their points added
		 */

		final LinkedList<Path> nextPathsToAdd = new LinkedList<>();
		final Set<Path> pathsAlreadyDone = new HashSet<>();

		final Path firstPath = structuredPathSet.iterator().next();
		if (firstPath.size() == 0) throw new SWCExportException(
			"The primary path contained no points!");
		nextPathsToAdd.add(firstPath);

		while (nextPathsToAdd.size() > 0) {

			final Path currentPath = nextPathsToAdd.removeFirst();

			if (!paths.contains(currentPath)) throw new SWCExportException(
				"The path \"" + currentPath +
					"\" is connected to other selected paths, but wasn't itself selected.");

			/*
			 * The paths we're dealing with specify connectivity, but we might be using the
			 * fitted versions - take them for the point positions:
			 */

			Path pathToUse = currentPath;
			if (currentPath.getUseFitted()) {
				pathToUse = currentPath.fitted;
			}

			Path parent = null;

			for (final Path possibleParent : currentPath.somehowJoins) {
				if (pathsAlreadyDone.contains(possibleParent)) {
					parent = possibleParent;
					break;
				}
			}

			int indexToStartAt = 0;
			int nearestParentSWCPointID = -1;
			PointInImage connectingPoint = null;
			if (parent != null) {
				if (currentPath.getStartJoins() != null && currentPath
					.getStartJoins() == parent) connectingPoint =
						currentPath.startJoinsPoint;
				else if (currentPath.endJoins != null && currentPath.endJoins == parent)
					connectingPoint = currentPath.endJoinsPoint;
				else if (parent.getStartJoins() != null && parent
					.getStartJoins() == currentPath) connectingPoint =
						parent.startJoinsPoint;
				else if (parent.endJoins != null && parent.endJoins == currentPath)
					connectingPoint = parent.endJoinsPoint;
				else throw new SWCExportException(
					"Couldn't find the link between parent \"" + parent +
						"\"\nand child \"" + currentPath + "\" which are somehow joined");

				/* Find the SWC point ID on the parent which is nearest: */

				double distanceSquaredToNearestParentPoint = Double.MAX_VALUE;
				for (final SWCPoint s : result) {
					if (s.getOnPath() != parent) continue;
					final double distanceSquared = connectingPoint.distanceSquaredTo(s.x,
						s.y, s.z);
					if (distanceSquared < distanceSquaredToNearestParentPoint) {
						nearestParentSWCPointID = s.id;
						distanceSquaredToNearestParentPoint = distanceSquared;
					}
				}

				/*
				 * Now find the index of the point on this path which is nearest
				 */
				indexToStartAt = pathToUse.indexNearestTo(connectingPoint.x,
					connectingPoint.y, connectingPoint.z);
			}

			SWCPoint firstSWCPoint = null;

			final boolean realRadius = pathToUse.hasRadii();
			for (int i = indexToStartAt; i < pathToUse.size(); ++i) {
				double radius = 0;
				if (realRadius) radius = pathToUse.radii[i];
				final SWCPoint swcPoint = new SWCPoint(currentPointID, pathToUse
					.getSWCType(), pathToUse.precise_x_positions[i],
					pathToUse.precise_y_positions[i], pathToUse.precise_z_positions[i],
					radius, firstSWCPoint == null ? nearestParentSWCPointID
						: currentPointID - 1);
				swcPoint.setOnPath(currentPath);
				result.add(swcPoint);
				++currentPointID;
				if (firstSWCPoint == null) firstSWCPoint = swcPoint;
			}

			boolean firstOfOtherBranch = true;
			for (int i = indexToStartAt - 1; i >= 0; --i) {
				int previousPointID = currentPointID - 1;
				if (firstOfOtherBranch) {
					firstOfOtherBranch = false;
					previousPointID = firstSWCPoint.id;
				}
				double radius = 0;
				if (realRadius) radius = pathToUse.radii[i];
				final SWCPoint swcPoint = new SWCPoint(currentPointID, pathToUse
					.getSWCType(), pathToUse.precise_x_positions[i],
					pathToUse.precise_y_positions[i], pathToUse.precise_z_positions[i],
					radius, previousPointID);
				swcPoint.setOnPath(currentPath);
				result.add(swcPoint);
				++currentPointID;
			}

			pathsAlreadyDone.add(currentPath);

			/*
			 * Add all the connected paths that aren't already in pathsAlreadyDone
			 */

			for (final Path connectedPath : currentPath.somehowJoins) {
				if (!pathsAlreadyDone.contains(connectedPath)) {
					nextPathsToAdd.add(connectedPath);
				}
			}
		}

		// Now check that all selectedPaths are in pathsAlreadyDone, otherwise
		// give an error:

		Path disconnectedExample = null;
		int selectedAndNotConnected = 0;
		for (final Path selectedPath : paths) {
			if (!pathsAlreadyDone.contains(selectedPath)) {
				++selectedAndNotConnected;
				if (disconnectedExample == null) disconnectedExample = selectedPath;
			}
		}
		if (selectedAndNotConnected > 0) throw new SWCExportException(
			"You must select all the connected paths\n(" + selectedAndNotConnected +
				" paths (e.g. \"" + disconnectedExample + "\") were not connected.)");

		return result;
	}

	public synchronized void resetListeners(final Path justAdded) {
		resetListeners(justAdded, false);
	}

	protected synchronized void resetListeners(final Path justAdded,
		final boolean expandAll)
	{

		final ArrayList<String> pathListEntries = new ArrayList<>();

		for (final Path p : allPaths) {
			if (p == null) {
				throw new IllegalArgumentException("BUG: A path in allPaths was null!");
			}
			pathListEntries.add(p.realToString());
		}

		for (final PathAndFillListener listener : listeners)
			listener.setPathList(pathListEntries.toArray(new String[] {}), justAdded,
				expandAll);

		final int fills = allFills.size();

		final String[] fillListEntries = new String[fills];

		for (int i = 0; i < fills; ++i) {

			final Fill f = allFills.get(i);
			if (f == null) {
				SNT.log("fill was null with i " + i + " out of " + fills);
				continue;
			}

			String name = "Fill (" + i + ")";

			if ((f.sourcePaths != null) && (f.sourcePaths.size() > 0)) {
				name += " from paths: " + f.getSourcePathsStringHuman();
			}
			fillListEntries[i] = name;
		}

		for (final PathAndFillListener pafl : listeners)
			pafl.setFillList(fillListEntries);

	}

	/**
	 * Adds the path.
	 *
	 * @param p the Path to be added
	 */
	public void addPath(final Path p) {
		addPath(p, false);
	}

	@SuppressWarnings("deprecation")
	protected synchronized void addPath(final Path p,
		final boolean forceNewName)
	{
		if (getPathFromID(p.getID()) != null) throw new IllegalArgumentException(
			"Attempted to add a path with an ID that was already added");
		if (p.getID() < 0) {
			p.setID(++maxUsedID);
		}
		if (maxUsedID < p.getID()) maxUsedID = p.getID();
		if (p.getName() == null || forceNewName) {
			final String suggestedName = getDefaultName(p);
			p.setName(suggestedName);
		}
		// Now check if there's already a path with this name.
		// If so, try adding numbered suffixes:
		final String originalName = p.getName();
		String candidateName = originalName;
		int numberSuffix = 2;
		while (getPathFromName(candidateName, false) != null) {
			candidateName = originalName + " (" + numberSuffix + ")";
			++numberSuffix;
		}
		p.setName(candidateName);
		/*
		 * Generate a new content3D, since it matters that the path is added with the
		 * right name via update3DViewerContents:
		 */
		if (plugin != null && plugin.use3DViewer) {
			p.removeFrom3DViewer(plugin.univ);
			p.addTo3DViewer(plugin.univ, plugin.deselectedColor3f, plugin.colorImage);
		}
		allPaths.add(p);
		resetListeners(p);
	}

	/*
	 * Find the default name for a new path, making sure it doesn't collide with any
	 * of the existing names:
	 */
	protected String getDefaultName(final Path p) {
		if (p.getID() < 0) throw new RuntimeException(
			"A path's ID should never be negative");
		return "Path (" + p.getID() + ")";
	}

	/**
	 * Deletes a Path by index
	 *
	 * @param index the index (zero-based) of the Path to be deleted
	 */
	public synchronized void deletePath(final int index) {
		deletePath(index, true);
	}

	/**
	 * Deletes a path.
	 *
	 * @param p the path to be deleted
	 * @throws IllegalArgumentException if path was not found
	 */
	public synchronized void deletePath(final Path p)
		throws IllegalArgumentException
	{
		final int i = getPathIndex(p);
		if (i < 0) throw new IllegalArgumentException(
			"Trying to delete a non-existent path: " + p);
		deletePath(i);
	}

	/**
	 * Gets the index of a Path
	 *
	 * @param p the Path for which the index should be retrieved
	 * @return the path index, or -1 if p was not found
	 */
	public synchronized int getPathIndex(final Path p) {
		int i = 0;
		for (i = 0; i < allPaths.size(); ++i) {
			if (p == allPaths.get(i)) return i;
		}
		return -1;
	}

	private synchronized void deletePath(final int index,
		final boolean updateInterface)
	{

		final Path originalPathToDelete = allPaths.get(index);

		Path unfittedPathToDelete = null;
		Path fittedPathToDelete = null;

		if (originalPathToDelete.fittedVersionOf == null) {
			unfittedPathToDelete = originalPathToDelete;
			fittedPathToDelete = originalPathToDelete.fitted;
		}
		else {
			unfittedPathToDelete = originalPathToDelete.fittedVersionOf;
			fittedPathToDelete = originalPathToDelete;
		}

		allPaths.remove(unfittedPathToDelete);
		if (fittedPathToDelete != null) allPaths.remove(fittedPathToDelete);

		// We don't just delete; have to fix up the references
		// in other paths (for start and end joins):

		for (final Path p : allPaths) {
			if (p.getStartJoins() == unfittedPathToDelete) {
				p.startJoins = null;
				p.startJoinsPoint = null;
			}
			if (p.endJoins == unfittedPathToDelete) {
				p.endJoins = null;
				p.endJoinsPoint = null;
			}
		}

		selectedPathsSet.remove(fittedPathToDelete);
		selectedPathsSet.remove(unfittedPathToDelete);

		if (plugin != null && plugin.use3DViewer) {
			if (fittedPathToDelete != null && fittedPathToDelete.content3D != null)
				fittedPathToDelete.removeFrom3DViewer(plugin.univ);
			if (unfittedPathToDelete.content3D != null) unfittedPathToDelete
				.removeFrom3DViewer(plugin.univ);
		}

		if (updateInterface) resetListeners(null);
	}

	/**
	 * Delete paths by position.
	 *
	 * @param indices the indices to be deleted
	 */
	public void deletePaths(final int[] indices) {
		Arrays.sort(indices);
		enableUIupdates = false;
		for (int i = indices.length - 1; i >= 0; --i) {
			deletePath(indices[i], false);
		}
		enableUIupdates = true;
		resetListeners(null);
	}

	protected void addFill(final Fill fill) {
		allFills.add(fill);
		resetListeners(null);
	}

	protected void deleteFills(final int[] indices) {
		Arrays.sort(indices);
		for (int i = indices.length - 1; i >= 0; --i) {
			deleteFill(indices[i], false);
		}
		resetListeners(null);
	}

	private synchronized void deleteFill(final int index,
		final boolean updateInterface)
	{
		allFills.remove(index);
		if (updateInterface) resetListeners(null);
	}

	protected void reloadFill(final int index) {
		final Fill toReload = allFills.get(index);
		plugin.startFillerThread(FillerThread.fromFill(plugin.getImagePlus(),
			plugin.stackMin, plugin.stackMax, true, toReload));

	}

	// FIXME: should probably use XMLStreamWriter instead of this ad-hoc
	// approach:
	synchronized protected void writeXML(final String fileName,
		final boolean compress) throws IOException
	{

		PrintWriter pw = null;

		try {
			if (compress) pw = new PrintWriter(new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(fileName)), StandardCharsets.UTF_8));
			else pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
				fileName), StandardCharsets.UTF_8));

			pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			pw.println("<!DOCTYPE tracings [");
			pw.println(
				"  <!ELEMENT tracings       (samplespacing,imagesize,path*,fill*)>");
			pw.println("  <!ELEMENT imagesize      EMPTY>");
			pw.println("  <!ELEMENT samplespacing  EMPTY>");
			pw.println("  <!ELEMENT path           (point+)>");
			pw.println("  <!ELEMENT point          EMPTY>");
			pw.println("  <!ELEMENT fill           (node*)>");
			pw.println("  <!ELEMENT node           EMPTY>");
			pw.println(
				"  <!ATTLIST samplespacing  x                 CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST samplespacing  y                 CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST samplespacing  z                 CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST samplespacing  units             CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST imagesize      width             CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST imagesize      height            CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST imagesize      depth             CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST path           id                CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST path           primary           CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           name              CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           startson          CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           startsindex       CDATA           #IMPLIED>"); // deprecated
			pw.println(
				"  <!ATTLIST path           startsx           CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           startsy           CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           startsz           CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           endson            CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           endsindex         CDATA           #IMPLIED>"); // deprecated
			pw.println(
				"  <!ATTLIST path           endsx             CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           endsy             CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           endsz             CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           reallength        CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           usefitted         (true|false)    #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           fitted            CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           fittedversionof   CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           swctype           CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           color             CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST path           channel           CDATA           #IMPLIED>");
			pw.println(
					"  <!ATTLIST path         frame             CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          x                 CDATA           #REQUIRED>"); // deprecated
			pw.println(
				"  <!ATTLIST point          y                 CDATA           #REQUIRED>"); // deprecated
			pw.println(
				"  <!ATTLIST point          z                 CDATA           #REQUIRED>"); // deprecated
			pw.println(
				"  <!ATTLIST point          xd                CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          yd                CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          zd                CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          tx                CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          ty                CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          tz                CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST point          r                 CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST fill           id                CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST fill           frompaths         CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST fill           metric            CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST fill           threshold         CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST fill           volume            CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST node           id                CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST node           x                 CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST node           y                 CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST node           z                 CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST node           previousid        CDATA           #IMPLIED>");
			pw.println(
				"  <!ATTLIST node           distance          CDATA           #REQUIRED>");
			pw.println(
				"  <!ATTLIST node           status            (open|closed)   #REQUIRED>");
			pw.println("]>");
			pw.println("");

			pw.println("<tracings>");

			pw.println("  <samplespacing x=\"" + x_spacing + "\" " + "y=\"" +
				y_spacing + "\" " + "z=\"" + z_spacing + "\" " + "units=\"" +
				spacing_units + "\"/>");

			if (plugin != null) pw.println("  <imagesize width=\"" + plugin.width +
				"\" height=\"" + plugin.height + "\" depth=\"" + plugin.depth + "\"/>");

			for (final Path p : allPaths) {
				// This probably should be a String returning
				// method of Path.
				pw.print("  <path id=\"" + p.getID() + "\"");
				pw.print(" swctype=\"" + p.getSWCType() + "\"");
				pw.print(" color=\"" + SNT.getColorString(p.getColor()) + "\"");
				pw.print(" channel=\"" + p.getChannel() + "\"");
				pw.print(" frame=\"" + p.getFrame() + "\"");

				String startsString = "";
				String endsString = "";
				if (p.startJoins != null) {
					final int startPathID = p.startJoins.getID();
					// Find the nearest index for backward compatibility:
					int nearestIndexOnStartPath = -1;
					if (p.startJoins.size() > 0) {
						nearestIndexOnStartPath = p.startJoins.indexNearestTo(
							p.startJoinsPoint.x, p.startJoinsPoint.y, p.startJoinsPoint.z);
					}
					startsString = " startson=\"" + startPathID + "\"" + " startx=\"" +
						p.startJoinsPoint.x + "\"" + " starty=\"" + p.startJoinsPoint.y +
						"\"" + " startz=\"" + p.startJoinsPoint.z + "\"";
					if (nearestIndexOnStartPath >= 0) startsString += " startsindex=\"" +
						nearestIndexOnStartPath + "\"";
				}
				if (p.endJoins != null) {
					final int endPathID = p.endJoins.getID();
					// Find the nearest index for backward compatibility:
					int nearestIndexOnEndPath = -1;
					if (p.endJoins.size() > 0) {
						nearestIndexOnEndPath = p.endJoins.indexNearestTo(p.endJoinsPoint.x,
							p.endJoinsPoint.y, p.endJoinsPoint.z);
					}
					endsString = " endson=\"" + endPathID + "\"" + " endsx=\"" +
						p.endJoinsPoint.x + "\"" + " endsy=\"" + p.endJoinsPoint.y + "\"" +
						" endsz=\"" + p.endJoinsPoint.z + "\"";
					if (nearestIndexOnEndPath >= 0) endsString += " endsindex=\"" +
						nearestIndexOnEndPath + "\"";
				}
				if (p.isPrimary()) pw.print(" primary=\"true\"");
				pw.print(" usefitted=\"" + p.getUseFitted() + "\"");
				if (p.fitted != null) {
					pw.print(" fitted=\"" + p.fitted.getID() + "\"");
				}
				if (p.fittedVersionOf != null) {
					pw.print(" fittedversionof=\"" + p.fittedVersionOf.getID() + "\"");
				}
				pw.print(startsString);
				pw.print(endsString);
				if (p.getName() != null) {
					pw.print(" name=\"" + XMLFunctions.escapeForXMLAttributeValue(p
						.getName()) + "\"");
				}
				pw.print(" reallength=\"" + p.getLength() + "\"");
				pw.println(">");

				for (int i = 0; i < p.size(); ++i) {
					final int px = p.getXUnscaled(i);
					final int py = p.getYUnscaled(i);
					final int pz = p.getZUnscaled(i);
					final double pxd = p.precise_x_positions[i];
					final double pyd = p.precise_y_positions[i];
					final double pzd = p.precise_z_positions[i];
					String attributes = "x=\"" + px + "\" " + "y=\"" + py + "\" z=\"" +
						pz + "\" " + "xd=\"" + pxd + "\" yd=\"" + pyd + "\" zd=\"" + pzd +
						"\"";
					if (p.hasRadii()) {
						attributes += " tx=\"" + p.tangents_x[i] + "\"";
						attributes += " ty=\"" + p.tangents_y[i] + "\"";
						attributes += " tz=\"" + p.tangents_z[i] + "\"";
						attributes += " r=\"" + p.radii[i] + "\"";
					}
					pw.println("    <point " + attributes + "/>");
				}
				pw.println("  </path>");
			}
			// Now output the fills:
			int fillIndex = 0;
			for (final Fill f : allFills) {
				f.writeXML(pw, fillIndex);
				++fillIndex;
			}
			pw.println("</tracings>");
		}
		finally {
			if (pw != null) pw.close();
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(final String uri, final String localName,
		final String qName, final Attributes attributes)
		throws TracesFileFormatException
	{

		if (boundingBox == null) boundingBox = new BoundingBox();
		switch (qName) {
			case "tracings":

				startJoins = new HashMap<>();
				startJoinsIndices = new HashMap<>();
				startJoinsPoints = new HashMap<>();
				endJoins = new HashMap<>();
				endJoinsIndices = new HashMap<>();
				endJoinsPoints = new HashMap<>();
				useFittedFields = new HashMap<>();
				fittedFields = new HashMap<>();
				fittedVersionOfFields = new HashMap<>();

				sourcePathIDForFills = new ArrayList<>();
				foundIDs = new HashSet<>();

				last_fill_id = -1;

				/*
				 * We need to remove the old paths and fills before loading the ones:
				 */
				SNT.log("Clearing old paths and fills...");
				clearPathsAndFills();

				break;
			case "imagesize":

				try {
					final int parsed_width = Integer.parseInt(attributes.getValue("width"));
					final int parsed_height = Integer.parseInt(attributes.getValue(
							"height"));
					final int parsed_depth = Integer.parseInt(attributes.getValue("depth"));
					if (plugin != null && (parsed_width != plugin.width ||
							parsed_height != plugin.height || parsed_depth != plugin.depth)) {
						SNT.warn(
								"The image size in the traces file didn't match - it's probably for another image");
					}
					boundingBox.setOrigin(new PointInImage(0, 0, 0));
					boundingBox.setDimensions(parsed_width, parsed_height, parsed_depth);
				} catch (final NumberFormatException e) {
					throw new TracesFileFormatException(
							"There was an invalid attribute to <imagesize/>: " + e);
				}

				break;
			case "samplespacing":

				try {
					final String xString = attributes.getValue("x");
					final String yString = attributes.getValue("y");
					final String zString = attributes.getValue("z");
					final String spacingUnits = attributes.getValue("units");
					boundingBox.setUnit(spacingUnits);
					boundingBox.xSpacing = Double.parseDouble(xString);
					boundingBox.ySpacing = Double.parseDouble(yString);
					boundingBox.zSpacing = Double.parseDouble(zString);
					if (spacingIsUnset) {
						x_spacing = boundingBox.xSpacing;
						y_spacing = boundingBox.ySpacing;
						z_spacing = boundingBox.zSpacing;
						spacing_units = spacingUnits;
						spacingIsUnset = false;
					}
				} catch (final NumberFormatException e) {
					throw new TracesFileFormatException(
							"There was an invalid attribute to <samplespacing/>: " + e);
				}

				break;
			case "path":

				final String idString = attributes.getValue("id");

				final String swcTypeString = attributes.getValue("swctype");
				final String colorString = attributes.getValue("color");
				final String channelString = attributes.getValue("channel");
				final String frameString = attributes.getValue("frame");
				final String useFittedString = attributes.getValue("usefitted");
				final String fittedIDString = attributes.getValue("fitted");
				final String fittedVersionOfIDString = attributes.getValue(
						"fittedversionof");
				final String startsonString = attributes.getValue("startson");
				final String startsindexString = attributes.getValue("startsindex");
				final String startsxString = attributes.getValue("startsx");
				final String startsyString = attributes.getValue("startsy");
				final String startszString = attributes.getValue("startsz");
				final String endsonString = attributes.getValue("endson");
				final String endsindexString = attributes.getValue("endsindex");
				final String endsxString = attributes.getValue("endsx");
				final String endsyString = attributes.getValue("endsy");
				final String endszString = attributes.getValue("endsz");
				final String nameString = attributes.getValue("name");
				final String primaryString = attributes.getValue("primary");

				if (startsxString == null && startsyString == null &&
						startszString == null) {
				} else if (startsxString != null && startsyString != null &&
						startszString != null) {
				} else {
					throw new TracesFileFormatException(
							"If one of starts[xyz] is specified, all of them must be.");
				}

				if (endsxString == null && endsyString == null && endszString == null) {
				} else if (endsxString != null && endsyString != null &&
						endszString != null) {
				} else {
					throw new TracesFileFormatException(
							"If one of ends[xyz] is specified, all of them must be.");
				}

				final boolean accurateStartProvided = startsxString != null;
				final boolean accurateEndProvided = endsxString != null;

				if (startsonString != null && (startsindexString == null &&
						!accurateStartProvided)) {
					throw new TracesFileFormatException(
							"If startson is specified for a path, then startsindex or starts[xyz] must also be specified.");
				}

				if (endsonString != null && (endsindexString == null &&
						!accurateEndProvided)) {
					throw new TracesFileFormatException(
							"If endson is specified for a path, then endsindex or ends[xyz] must also be specified.");
				}

				current_path = new Path(x_spacing, y_spacing, z_spacing, spacing_units);

				int startson, endson, endsindex;
				Integer startsOnInteger = null;
				Integer startsIndexInteger = null;
				PointInImage startJoinPoint = null;
				Integer endsOnInteger = null;
				Integer endsIndexInteger = null;
				PointInImage endJoinPoint = null;

				Integer fittedIDInteger = null;
				Integer fittedVersionOfIDInteger = null;

				if (primaryString != null && primaryString.equals("true")) current_path
						.setIsPrimary(true);

				int id = -1;

				try {

					id = Integer.parseInt(idString);
					if (foundIDs.contains(id)) {
						throw new TracesFileFormatException(
								"There is more than one path with ID " + id);
					}
					current_path.setID(id);
					if (id > maxUsedID) maxUsedID = id;

					if (swcTypeString != null) {
						final int swcType = Integer.parseInt(swcTypeString);
						current_path.setSWCType(swcType, false);
					}

					if (colorString != null) {
						current_path.setColor(SNT.getColor(colorString));
					}
					if (channelString != null && frameString != null) {
						current_path.setCTposition(Integer.parseInt(channelString), Integer
								.parseInt(frameString));
					}

					if (startsonString == null) {
						startson = -1;
					} else {
						startson = Integer.parseInt(startsonString);
						startsOnInteger = startson;

						if (startsxString == null) {
							// The index (older file format) was supplied:
							startsIndexInteger = new Integer(startsindexString);
						} else {
							startJoinPoint = new PointInImage(Double.parseDouble(startsxString),
									Double.parseDouble(startsyString), Double.parseDouble(
									startszString));
						}
					}

					if (endsonString == null) endson = endsindex = -1;
					else {
						endson = Integer.parseInt(endsonString);
						endsOnInteger = endson;

						if (endsxString != null) {
							endJoinPoint = new PointInImage(Double.parseDouble(endsxString),
									Double.parseDouble(endsyString), Double.parseDouble(endszString));
						} else {
							// The index (older file format) was supplied:
							endsindex = Integer.parseInt(endsindexString);
							endsIndexInteger = endsindex;
						}
					}

					if (fittedVersionOfIDString != null) fittedVersionOfIDInteger =
							Integer.parseInt(fittedVersionOfIDString);
					if (fittedIDString != null) fittedIDInteger = Integer
							.parseInt(fittedIDString);

				} catch (final NumberFormatException e) {
					e.printStackTrace();
					throw new TracesFileFormatException(
							"There was an invalid attribute in <path/>: " + e);
				}

				current_path.setName(nameString); // default name if null


				if (startsOnInteger != null) startJoins.put(id, startsOnInteger);
				if (endsOnInteger != null) endJoins.put(id, endsOnInteger);

				if (startJoinPoint != null) startJoinsPoints.put(id, startJoinPoint);
				if (endJoinPoint != null) endJoinsPoints.put(id, endJoinPoint);

				if (startsIndexInteger != null) {
					startJoinsIndices.put(id, startsIndexInteger);
				}
				if (endsIndexInteger != null) endJoinsIndices.put(id, endsIndexInteger);

				if (useFittedString == null) useFittedFields.put(id, false);
				else {
					if (useFittedString.equals("true")) useFittedFields.put(id, true);
					else if (useFittedString.equals("false")) useFittedFields.put(id,
							false);
					else {
						throw new TracesFileFormatException(
								"Unknown value for 'fitted' attribute: '" + useFittedString + "'");
					}
				}

				if (fittedIDInteger != null) fittedFields.put(id, fittedIDInteger);
				if (fittedVersionOfIDInteger != null) fittedVersionOfFields.put(id,
						fittedVersionOfIDInteger);

				break;
			case "point":

				try {

					double parsed_xd, parsed_yd, parsed_zd;

					final String xdString = attributes.getValue("xd");
					final String ydString = attributes.getValue("yd");
					final String zdString = attributes.getValue("zd");

					final String xString = attributes.getValue("x");
					final String yString = attributes.getValue("y");
					final String zString = attributes.getValue("z");

					if (xdString != null && ydString != null && zdString != null) {
						parsed_xd = Double.parseDouble(xdString);
						parsed_yd = Double.parseDouble(ydString);
						parsed_zd = Double.parseDouble(zdString);
					} else if (xdString != null || ydString != null || zdString != null) {
						throw new TracesFileFormatException(
								"If one of the attributes xd, yd or zd to the point element is specified, they all must be.");
					} else if (xString != null && yString != null && zString != null) {
						parsed_xd = boundingBox.xSpacing * Integer.parseInt(xString);
						parsed_yd = boundingBox.ySpacing * Integer.parseInt(yString);
						parsed_zd = boundingBox.zSpacing * Integer.parseInt(zString);
					} else if (xString != null || yString != null || zString != null) {
						throw new TracesFileFormatException(
								"If one of the attributes x, y or z to the point element is specified, they all must be.");
					} else {
						throw new TracesFileFormatException(
								"Each point element must have at least the attributes (x, y and z) or (xd, yd, zd)");
					}

					current_path.addPointDouble(parsed_xd, parsed_yd, parsed_zd);

					final int lastIndex = current_path.size() - 1;
					final String radiusString = attributes.getValue("r");
					final String tXString = attributes.getValue("tx");
					final String tYString = attributes.getValue("ty");
					final String tZString = attributes.getValue("tz");

					if (radiusString != null && tXString != null && tYString != null &&
							tZString != null) {
						if (lastIndex == 0)
							// Then we've just started, create the arrays in Path:
							current_path.createCircles();
						else if (!current_path.hasRadii())
							throw new TracesFileFormatException("The point at index " +
									lastIndex + " had a fitted circle, but none previously did");
						current_path.tangents_x[lastIndex] = Double.parseDouble(tXString);
						current_path.tangents_y[lastIndex] = Double.parseDouble(tYString);
						current_path.tangents_z[lastIndex] = Double.parseDouble(tZString);
						current_path.radii[lastIndex] = Double.parseDouble(radiusString);
					} else if (radiusString != null || tXString != null || tYString != null ||
							tZString != null) throw new TracesFileFormatException(
							"If one of the r, tx, ty or tz attributes to the point element is specified, they all must be");
					else {
						// All circle attributes are null:
						if (current_path.hasRadii()) throw new TracesFileFormatException(
								"The point at index " + lastIndex +
										" had no fitted circle, but all previously did");
					}

				} catch (final NumberFormatException e) {
					throw new TracesFileFormatException(
							"There was an invalid attribute to <imagesize/>");
				}

				break;
			case "fill":

				try {

					String[] sourcePaths = {};
					final String fromPathsString = attributes.getValue("frompaths");
					if (fromPathsString != null) sourcePaths = fromPathsString.split(", *");

					current_fill = new Fill();

					final String metric = attributes.getValue("metric");
					current_fill.setMetric(metric);

					last_fill_node_id = -1;

					final String fill_id_string = attributes.getValue("id");

					int fill_id = Integer.parseInt(fill_id_string);

					if (fill_id < 0) {
						throw new TracesFileFormatException(
								"Can't have a negative id in <fill>");
					}

					if (fill_id != (last_fill_id + 1)) {
						SNT.log("Out of order id in <fill> (" + fill_id +
								" when we were expecting " + (last_fill_id + 1) + ")");
						fill_id = last_fill_id + 1;
					}

					final int[] sourcePathIndices = new int[sourcePaths.length];

					for (int i = 0; i < sourcePaths.length; ++i)
						sourcePathIndices[i] = Integer.parseInt(sourcePaths[i]);

					sourcePathIDForFills.add(sourcePathIndices);

					last_fill_id = fill_id;

					final String thresholdString = attributes.getValue("threshold");
					final double fillThreshold = Double.parseDouble(thresholdString);

					current_fill.setThreshold(fillThreshold);

				} catch (final NumberFormatException e) {
					throw new TracesFileFormatException(
							"There was an invalid attribute to <fill>");
				}

				break;
			case "node":

				try {

					final String xString = attributes.getValue("x");
					final String yString = attributes.getValue("y");
					final String zString = attributes.getValue("z");
					final String nIdString = attributes.getValue("id");
					final String distanceString = attributes.getValue("distance");
					final String previousString = attributes.getValue("previousid");

					final int parsed_x = Integer.parseInt(xString);
					final int parsed_y = Integer.parseInt(yString);
					final int parsed_z = Integer.parseInt(zString);
					final int parsed_id = Integer.parseInt(nIdString);
					final double parsed_distance = Double.parseDouble(distanceString);
					int parsed_previous;
					if (previousString == null) parsed_previous = -1;
					else parsed_previous = Integer.parseInt(previousString);

					if (parsed_id != (last_fill_node_id + 1)) {
						throw new TracesFileFormatException(
								"Fill node IDs weren't consecutive integers");
					}

					final String openString = attributes.getValue("status");

					current_fill.add(parsed_x, parsed_y, parsed_z, parsed_distance,
							parsed_previous, openString.equals("open"));

					last_fill_node_id = parsed_id;

				} catch (final NumberFormatException e) {
					throw new TracesFileFormatException(
							"There was an invalid attribute to <node/>: " + e);
				}

				break;
			default:
				throw new TracesFileFormatException("Unknown element: '" + qName + "'");
		}

	}

	@SuppressWarnings("deprecation")
	private void addTo3DViewer(final Path p) {
		if (plugin != null && plugin.use3DViewer && p.fittedVersionOf == null && p
			.size() > 1)
		{
			Path pathToAdd;
			if (p.getUseFitted()) pathToAdd = p.fitted;
			else pathToAdd = p;
			pathToAdd.addTo3DViewer(plugin.univ, plugin.deselectedColor,
				plugin.colorImage);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(final String uri, final String localName,
		final String qName) throws TracesFileFormatException
	{

		switch (qName) {
			case "path":

				allPaths.add(current_path);

				break;
			case "fill":

				allFills.add(current_fill);

				break;
			case "tracings":

				// Then we've finished...

				for (final Path p : allPaths) {
					final Integer startID = startJoins.get(p.getID());
					final Integer startIndexInteger = startJoinsIndices.get(p.getID());
					PointInImage startJoinPoint = startJoinsPoints.get(p.getID());
					final Integer endID = endJoins.get(p.getID());
					final Integer endIndexInteger = endJoinsIndices.get(p.getID());
					PointInImage endJoinPoint = endJoinsPoints.get(p.getID());
					final Integer fittedID = fittedFields.get(p.getID());
					final Integer fittedVersionOfID = fittedVersionOfFields.get(p.getID());
					final Boolean useFitted = useFittedFields.get(p.getID());

					if (startID != null) {
						final Path startPath = getPathFromID(startID);
						if (startJoinPoint == null) {
							// Then we have to get it from startIndexInteger:
							startJoinPoint = startPath.getNode(startIndexInteger);
						}
						p.setStartJoin(startPath, startJoinPoint);
					}
					if (endID != null) {
						final Path endPath = getPathFromID(endID);
						if (endJoinPoint == null) {
							// Then we have to get it from endIndexInteger:
							endJoinPoint = endPath.getNode(endIndexInteger);
						}
						p.setEndJoin(endPath, endJoinPoint);
					}
					if (fittedID != null) {
						final Path fitted = getPathFromID(fittedID);
						p.fitted = fitted;
						p.setUseFitted(useFitted);
					}
					if (fittedVersionOfID != null) {
						final Path fittedVersionOf = getPathFromID(fittedVersionOfID);
						p.fittedVersionOf = fittedVersionOf;
					}
				}

				// Do some checks that the fitted and fittedVersionOf fields match
				// up:
				for (final Path p : allPaths) {
					if (p.fitted != null) {
						if (p.fitted.fittedVersionOf == null)
							throw new TracesFileFormatException(
									"Malformed traces file: p.fitted.fittedVersionOf was null");
						else if (p != p.fitted.fittedVersionOf)
							throw new TracesFileFormatException(
									"Malformed traces file: p didn't match p.fitted.fittedVersionOf");
					} else if (p.fittedVersionOf != null) {
						if (p.fittedVersionOf.fitted == null)
							throw new TracesFileFormatException(
									"Malformed traces file: p.fittedVersionOf.fitted was null");
						else if (p != p.fittedVersionOf.fitted)
							throw new TracesFileFormatException(
									"Malformed traces file: p didn't match p.fittedVersionOf.fitted");
					}
					if (p.useFitted && p.fitted == null) {
						throw new TracesFileFormatException(
								"Malformed traces file: p.useFitted was true but p.fitted was null");
					}
				}

				// Now we're safe to add them all to the 3D Viewer
				for (final Path p : allPaths) {
					addTo3DViewer(p);
				}

				// Now turn the source paths into real paths...
				for (int i = 0; i < allFills.size(); ++i) {
					final Fill f = allFills.get(i);
					final Set<Path> realSourcePaths = new HashSet<>();
					final int[] sourcePathIDs = sourcePathIDForFills.get(i);
					for (int sourcePathID : sourcePathIDs) {
						final Path sourcePath = getPathFromID(sourcePathID);
						if (sourcePath != null) realSourcePaths.add(sourcePath);
					}
					f.setSourcePaths(realSourcePaths);
				}

				setSelected(new ArrayList<Path>(), this);
				resetListeners(null, true);
				break;
		}

	}

	/**
	 * Creates a PathAndFillManager instance from imported data
	 *
	 * @param filePath the absolute path of the file to be imported as per
	 *          {@link #load(String)}
	 * @return the PathAndFillManager instance, or null if file could not be
	 *         imported
	 */
	public static PathAndFillManager createFromFile(final String filePath) {
		final PathAndFillManager pafm = new PathAndFillManager();
		pafm.setHeadless(true);
		if (pafm.load(filePath)) return pafm;
		else return null;
	}

	/**
	 * Creates a PathAndFillManager instance from a collection of reconstruction
	 * nodes.
	 *
	 * @param nodes the collection of reconstruction nodes. Nodes will be sorted by
	 *              id and any duplicate entries pruned.
	 * @return the PathAndFillManager instance, or null if file could not be
	 *         imported
	 */
	public static PathAndFillManager createFromNodes(final Collection<SWCPoint> nodes) {
		final PathAndFillManager pafm = new PathAndFillManager();
		pafm.setHeadless(true);
		final TreeSet<SWCPoint> set = (nodes instanceof TreeSet) ? (TreeSet<SWCPoint>) nodes
				: new TreeSet<SWCPoint>(nodes);
		if (pafm.importNodes(null, set, null, false))
			return pafm;
		else
			return null;
	}

	private boolean load(final InputStream is) {

		try {

			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setValidating(true);
			final SAXParser parser = factory.newSAXParser();

			if (is != null) parser.parse(is, this);
			else if (null != null) {
				final InputSource inputSource = new InputSource((Reader) null);
				parser.parse(inputSource, this);
			}

		}
		catch (final javax.xml.parsers.ParserConfigurationException e) {

			clearPathsAndFills();
			SNT.error("ParserConfigurationException", e);
			return false;

		}
		catch (final SAXException e) {

			clearPathsAndFills();
			error(e.getMessage());
			return false;

		}
		catch (final FileNotFoundException e) {

			clearPathsAndFills();
			SNT.error("FileNotFoundException", e);
			e.printStackTrace();
			return false;

		}
		catch (final IOException e) {

			clearPathsAndFills();
			error(
				"There was an IO exception while reading the file. See console for details");
			e.printStackTrace();
			return false;

		}

		return true;

	}

	private void clearPathsAndFills() {
		maxUsedID = -1;
		if (plugin != null && plugin.use3DViewer) {
			for (final Path p : allPaths)
				p.removeFrom3DViewer(plugin.univ);
		}
		allPaths.clear();
		allFills.clear();
		resetListeners(null);
	}

	/**
	 * Imports an SWC file using default settings.
	 *
	 * @param urlOrFilePath the URL pointing to the SWC file or the absolute file
	 *          path of a local file. Note that with URLs, https may not be
	 *          supported.
	 * @return true, if import was successful
	 * @see tracing.io.NeuroMorphoLoader
	 * @see #importSWC(String, boolean)
	 * @see #importSWC(BufferedReader, boolean, double, double, double, double,
	 *      double, double, boolean)
	 */
	public boolean importSWC(final String urlOrFilePath) {
		if (SNT.isValidURL(urlOrFilePath)) {
			try {
				final URL url = new URL(urlOrFilePath);
				final InputStream is = url.openStream();
				return importSWC(new BufferedReader(new InputStreamReader(is)));
			}
			catch (final IOException e) {
				return false;
			}
		}
		else {
			return importSWC(urlOrFilePath, false);
		}
	}

	/**
	 * Imports a group of SWC files (Remote URLs supported).
	 *
	 * @param swcs the HashMap containing the absolute file paths (or URLs) of
	 *          files to be imported as values and a file descriptor as keys.
	 * @param color the color to be applied to imported Paths. If null, paths from
	 *          each file will assigned unique colors
	 * @return the List of imported {@link Tree}s labeled after the file
	 *         descriptor. The returned list will not contain null elements: If a
	 *         file was not successfully imported an empty Tree will be generated
	 * @see Tree#isEmpty()
	 * @see Tree#getLabel()
	 * @see SNTColor#getDistinctColors(int)
	 */
	public List<Tree> importSWCs(final Map<String, String> swcs,
		final ColorRGB color)
	{
		final List<Tree> result = new ArrayList<>();
		final ColorRGB[] colors;
		if (color == null) {
			colors = SNTColor.getDistinctColors(swcs.size());
		}
		else {
			colors = new ColorRGB[swcs.size()];
			Arrays.fill(colors, color);
		}
		final boolean headlessState = PathAndFillManager.headless;
		setHeadless(true);
		final int[] colorIdx = { 0 };
		swcs.forEach((id, path) -> {
			SNT.log("Loading " + id + ": " + path);
			final Tree tree = new Tree();
			tree.setLabel(id);
			result.add(tree);
			final int firstImportedPathIdx = size();
			if (!importSWC(path)) {
				return; // here means 'continue;'
			}
			for (int i = firstImportedPathIdx; i < size(); i++) {
				final Path p = getPath(i);
				p.setName(p.getName() + "{" + id + "}"); // This is just for PathManager
																									// inability to deal with
																									// Trees
				p.setColorRGB(colors[colorIdx[0]]);
				tree.add(p);
			}
			colorIdx[0]++;
		});
		setHeadless(headlessState);
		return result;
	}

	private boolean importSWC(final BufferedReader br) throws IOException
	{
		return importSWC(br, false, 0, 0, 0, 1, 1, 1, false);
	}

	/**
	 * Imports SWC data with advanced settings. The SWC format is described in
	 * <a href="https://www.ncbi.nlm.nih.gov/pubmed/9821633">PMID 9821633</a> and
	 * e.g.,
	 * <a href="http://www.neuromorpho.org/myfaq.jsp#qr3">neuromorpho.org</a> It
	 * is named after the initials of Stockley, Wheal, and Cole, who earlier
	 * developed a pioneer system for morphometric reconstructions
	 * (<a href="https://www.ncbi.nlm.nih.gov/pubmed/8321013">PMID 8321013</a>).
	 * <p>
	 * Annoyingly, While the SWC specification details the usage of of world
	 * coordinates in microns, some published SWC files have adopted image (pixel)
	 * coordinates, which is inappropriate and less useful (an example of the
	 * latter seems to part of the DIADEM Challenge data set). In addition, it's
	 * not clear what the "radius" column is meant to mean in such files.
	 * </p>
	 *
	 * @param br the character stream containing the data
	 * @param assumeCoordinatesInVoxels If true, the SWC coordinates are assumed
	 *          to be in image coordinates ("pixels"). Note that in this case,
	 *          radii will be scaled by the minimum voxel separation. This
	 *          workaround seems to be required to properly import unscaled files
	 * @param xOffset the offset to be applied to all X coordinates. May be useful
	 *          to import data obtained from multiple "un-stitched" fields of
	 *          view. Default is 0.
	 * @param yOffset the offset to be applied to all Y coordinates. May be useful
	 *          to import data obtained from multiple "un-stitched" fields of
	 *          view. Default is 0.
	 * @param zOffset the offset to be applied to all Z coordinates. May be useful
	 *          to import data obtained from multiple "un-stitched" fields of
	 *          view. Default is 0.
	 * @param xScale the scaling factor for all X coordinates. Useful to import
	 *          data onto downsampled images. Default is 1.
	 * @param yScale the scaling factor for all Y coordinates. Useful to import
	 *          data onto downsampled images. Default is 1.
	 * @param zScale the scaling factor for all Z coordinates. Useful to import
	 *          data onto downsampled images. Default is 1.
	 * @param replaceAllPaths If true, all existing Paths will be deleted before
	 *          the import. Default is false.
	 * @return true, if import was successful
	 */
	protected boolean importSWC(final BufferedReader br,
		final boolean assumeCoordinatesInVoxels, final double xOffset,
		final double yOffset, final double zOffset, final double xScale,
		final double yScale, final double zScale, final boolean replaceAllPaths)
	{

		if (replaceAllPaths) clearPathsAndFills();

		final Pattern pEmpty = Pattern.compile("^\\s*$");
		final Pattern pComment = Pattern.compile("^([^#]*)#.*$");

		final TreeSet<SWCPoint> nodes = new TreeSet<>();
		String line;
		try {
			while ((line = br.readLine()) != null) {
				final Matcher mComment = pComment.matcher(line);
				line = mComment.replaceAll("$1").trim();
				final Matcher mEmpty = pEmpty.matcher(line);
				if (mEmpty.matches()) continue;
				final String[] fields = line.split("\\s+");
				if (fields.length < 7) {
					error("Wrong number of fields (" + fields.length + ") in line: " +
						line);
					return false;
				}
				try {
					final int id = Integer.parseInt(fields[0]);
					final int type = Integer.parseInt(fields[1]);
					final double x = xScale * Double.parseDouble(fields[2]) + xOffset;
					final double y = yScale * Double.parseDouble(fields[3]) + yOffset;
					final double z = zScale * Double.parseDouble(fields[4]) + zOffset;
					final double radius = Double.parseDouble(fields[5]);
					final int previous = Integer.parseInt(fields[6]);
					nodes.add(new SWCPoint(id, type, x, y, z, radius, previous));
				}
				catch (final NumberFormatException nfe) {
					error("There was a malformed number in line: " + line);
					return false;
				}
			}
		}
		catch (final IOException exc) {
			SNT.error("IO ERROR", exc);
			return false;
		}
		return importNodes(null, nodes, null, assumeCoordinatesInVoxels);
	}

	private boolean importNodes(final String descriptor,
	                            final TreeSet<SWCPoint> points, final ColorRGB color,
	                            final boolean assumeCoordinatesInVoxels)
	{

		final Map<Integer, SWCPoint> idToSWCPoint = new HashMap<>();
		final List<SWCPoint> primaryPoints = new ArrayList<>();

		final int firstImportedPathIdx = size();
		for (final SWCPoint point : points) {
			idToSWCPoint.put(point.id, point);
			if (point.parent == -1) {
				primaryPoints.add(point);
			}
			else {
				final SWCPoint previousPoint = idToSWCPoint.get(point.parent);
				if (previousPoint != null) {
					point.setPreviousPoint(previousPoint);
					previousPoint.getNextPoints().add(point);
				}
			}
		}

		if (spacingIsUnset) {
			if (!headless) SNT.log(
				"Inferring pixel spacing from imported points... ");
			boundingBox.inferSpacing(points);
			x_spacing = boundingBox.xSpacing;
			y_spacing = boundingBox.ySpacing;
			z_spacing = boundingBox.zSpacing;
			// NB: we must leave boundingBox origin unset so that it's dimensions can be properly computed
			spacingIsUnset = false;
		}

		// We'll now iterate (again!) through the points to fix some ill-assembled
		// files that do exist in the wild defined in pixel coordinates!
		if (assumeCoordinatesInVoxels) {
			final double minimumVoxelSpacing = Math.min(Math.abs(x_spacing),
					Math.min(Math.abs(y_spacing), Math.abs(z_spacing)));

			final Iterator<SWCPoint> it = points.iterator();
			while (it.hasNext()) {
				final SWCPoint point = it.next();

				point.x *= x_spacing;
				point.y *= y_spacing;
				point.z *= z_spacing;
				// this just seems to be the convention in the broken files we've came
				// across
				point.radius *= minimumVoxelSpacing;

				// If the radius is set to near zero, then artificially set it to half
				// of the voxel spacing so that something* appears in the 3D Viewer!
				if (Math.abs(point.radius) < 0.0000001)
					point.radius = minimumVoxelSpacing / 2;
			}
		}

		// FIXME: This is really slow with large SWC files
		final HashMap<SWCPoint, Path> pointToPath = new HashMap<>();
		final PriorityQueue<SWCPoint> backtrackTo = new PriorityQueue<>(
			primaryPoints);
		final HashMap<Path, SWCPoint> pathStartsOnSWCPoint = new HashMap<>();
		final HashMap<Path, PointInImage> pathStartsAtPointInImage =
			new HashMap<>();

		SWCPoint start;
		Path currentPath;
		while ((start = backtrackTo.poll()) != null) {
			currentPath = new Path(x_spacing, y_spacing, z_spacing, spacing_units);
			currentPath.createCircles();
			if (start.getPreviousPoint() != null) {
				final SWCPoint beforeStart = start.getPreviousPoint();
				pathStartsOnSWCPoint.put(currentPath, beforeStart);
				pathStartsAtPointInImage.put(currentPath, beforeStart
					.getPointInImage());
				currentPath.addNode(beforeStart);
			}

			// Now we can start adding points to the path:
			SWCPoint currentPoint = start;
			while (currentPoint != null) {
				currentPath.addNode(currentPoint);
				pointToPath.put(currentPoint, currentPath);

				if (currentPoint.getNextPoints().size() > 0) {
					final SWCPoint newCurrentPoint = currentPoint.getNextPoints().get(0);
					currentPoint.getNextPoints().remove(0);
					backtrackTo.addAll(currentPoint.getNextPoints());
					currentPoint = newCurrentPoint;
				}
				else {
					currentPath.setSWCType(currentPoint.type); // Assign point
					// type to path
					currentPoint = null;
				}
			}
			currentPath.setGuessedTangents(2);
			addPath(currentPath);
		}

		// Set the start joins:
		for (int i = firstImportedPathIdx; i < size(); i++) {
			final Path p = getPath(i);
			final SWCPoint swcPoint = pathStartsOnSWCPoint.get(p);
			if (descriptor != null) {
				p.setName(p.getName() + "{" + descriptor + "}");
				p.setColorRGB(color);
			}
			if (swcPoint == null) {
				p.setIsPrimary(true);
				continue;
			}
			final Path previousPath = pointToPath.get(swcPoint);
			final PointInImage pointInImage = pathStartsAtPointInImage.get(p);
			p.setStartJoin(previousPath, pointInImage);
		}

		resetListeners(null, true);

		// Infer fields for when an image has not been specified. We'll assume
		// the image dimensions to be those of the coordinates bounding box.
		// This allows us to open a SWC file without a source image
		{
			if (boundingBox == null)
				boundingBox = new BoundingBox();
			boundingBox.compute(((TreeSet<? extends SNTPoint>) points).iterator());

			// If a plugin exists, warn user if its image cannot render imported nodes
			if (plugin != null && plugin.getImagePlus() != null) {

				final BoundingBox pluginBoundingBox = new BoundingBox();
				pluginBoundingBox.setOrigin(new PointInImage(0, 0, 0));
				pluginBoundingBox.setDimensions(plugin.width, plugin.height, plugin.depth);
				if (!pluginBoundingBox.contains(boundingBox)) {
					SNT.warn("Some nodes lay outside the image volume: you may need to "
							+ "adjust import options or resize current image canvas");
				}
			}
		}
		return true;
	}

	/**
	 * Import neuron(s) as a collection of reconstruction nodes (SWC points)
	 *
	 * @param map         the input map of reconstruction nodes
	 * @param color       the color to be applied to imported Paths. If null, paths
	 *                    from each ID will assigned unique colors
	 * @param spatialUnit the spatial unit (um, mm, etc) associated with imported
	 *                    nodes. If null, "um" are assumed
	 * @return the map mapping imported ids to imported Trees. A null Tree will be
	 *         assigned if a morphology could not be imported
	 * @see SNTColor#getDistinctColors(int)
	 */
	public Map<String, Tree> importNeurons(final Map<String, TreeSet<SWCPoint>> map, final ColorRGB color, final String spatialUnit)
	{
		final Map<String, Tree> result = importMap(map, color);
		if (result.values().stream().anyMatch(tree -> tree != null && !tree.isEmpty())) {
			if (boundingBox == null) // should never happen
				boundingBox = new BoundingBox();
			boundingBox.setUnit((spatialUnit == null) ? "um" : spatialUnit);
		}
		return result;
	}

	private Map<String, Tree> importMap(final Map<String, TreeSet<SWCPoint>> map,
		final ColorRGB color)
	{
		final Map<String, Tree> result = new HashMap<>();
		final ColorRGB[] colors;
		if (color == null) {
			colors = SNTColor.getDistinctColors(map.size());
		}
		else {
			colors = new ColorRGB[map.size()];
			Arrays.fill(colors, color);
		}
		final int[] colorIdx = { 0 };
		map.forEach((k, points) -> {
			if (points == null) {
				SNT.error("Importing " + k + "... failed. Invalid structure?");
				result.put(k, null);
			}
			else {
				final int firstImportedPathIdx = size();
				SNT.log("Importing " + k + "...");
				final boolean success = importNodes(k, points, colors[colorIdx[0]],
						false);
				SNT.log("Successful import: " + success);
				final Tree tree = new Tree();
				tree.setLabel(k);
				for (int i = firstImportedPathIdx; i < size(); i++)
					tree.add(getPath(i));
				result.put(k, tree);
			}
			colorIdx[0]++;
		});
		return result;
	}

	/**
	 * Imports an SWC file.
	 *
	 * @param filePath the absolute path of the file to be imported
	 * @param ignoreCalibration the ignore calibration
	 * @return true, if import was successful
	 */
	protected boolean importSWC(final String filePath,
		final boolean ignoreCalibration)
	{
		return importSWC(filePath, ignoreCalibration, 0, 0, 0, 1, 1, 1, false);
	}

	/**
	 * Imports an SWC file using advanced options.
	 *
	 * @param filePath the absolute file path to the imported file
	 * @param assumeCoordinatesInVoxels see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param xOffset see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param yOffset see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param zOffset see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param xScale see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param yScale see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param zScale see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @param replaceAllPaths see
	 *          {@link #importSWC(BufferedReader, boolean, double, double, double, double, double, double, boolean)}
	 * @return true, if import was successful
	 */
	public boolean importSWC(final String filePath,
		final boolean assumeCoordinatesInVoxels, final double xOffset,
		final double yOffset, final double zOffset, final double xScale,
		final double yScale, final double zScale, final boolean replaceAllPaths)
	{

		if (filePath == null) return false;
		final File f = new File(filePath);
		if (!SNT.fileAvailable(f)) {
			error("The traces file '" + filePath + "' is not available.");
			return false;
		}

		InputStream is = null;
		boolean result = false;

		try {

			is = new BufferedInputStream(new FileInputStream(filePath));
			final BufferedReader br = new BufferedReader(new InputStreamReader(is,
					StandardCharsets.UTF_8));

			result = importSWC(br, assumeCoordinatesInVoxels, xOffset, yOffset,
				zOffset, xScale, yScale, zScale, replaceAllPaths);

			if (is != null) is.close();

		}
		catch (final IOException ioe) {
			error("Could not read " + filePath);
			return false;
		}

		return result;

	}

	protected static int guessTracesFileType(final String filename) {

		/*
		 * Look at the magic bytes at the start of the file:
		 *
		 * If this looks as if it's gzip compressed, assume it's a compressed traces
		 * file. If it begins "<?xml", assume it's an uncompressed traces file. If it
		 * begins with '{"' assume it is a ML JSON file, otherwise assume it's an SWC
		 * file.
		 */
		final File f = new File(filename);
		if (!SNT.fileAvailable(f)) {
			errorStatic("The file '" + filename + "' is not available.");
			return -1;
		}
		try {
			if (!headless) SNT.log("Guessing file type...");
			InputStream is;
			final byte[] buf = new byte[8];
			is = new FileInputStream(filename);
			is.read(buf, 0, 8);
			is.close();
			//SNT.log("buf[0]: " + buf[0] + ", buf[1]: " + buf[1]);
			if (((buf[0] & 0xFF) == 0x1F) && ((buf[1] & 0xFF) == 0x8B)) {
				return TRACES_FILE_TYPE_COMPRESSED_XML;
			}
			else if (((buf[0] == '<') && (buf[1] == '?') && (buf[2] == 'x') &&
				(buf[3] == 'm') && (buf[4] == 'l') && (buf[5] == ' '))) {
				return TRACES_FILE_TYPE_UNCOMPRESSED_XML;
			} else if (((char)(buf[0] & 0xFF) == '{')) {
				return TRACES_FILE_TYPE_ML_JSON;
			}
		}
		catch (final IOException e) {
			SNT.error("Couldn't read from file: " + filename, e);
			return -1;
		}
		return TRACES_FILE_TYPE_SWC;
	}

	protected boolean loadCompressedXML(final String filename) {
		try {
			SNT.log("Loading gzipped file...");
			return load(new GZIPInputStream(new BufferedInputStream(
				new FileInputStream(filename))));
		}
		catch (final IOException ioe) {
			error("Could not read file '" + filename +
				"' (it was expected to be compressed XML)");
			return false;
		}
	}

	protected boolean loadUncompressedXML(final String filename) {
		try {
			SNT.log("Loading uncompressed file...");
			return load(new BufferedInputStream(new FileInputStream(filename)));
		}
		catch (final IOException ioe) {
			error("Could not read '" + filename + "' (it was expected to be XML)");
			return false;
		}
	}

	private boolean loadJSON(final String filename) {
		try {
			final Map<String, TreeSet<SWCPoint>> nMap = MouseLightLoader.extractNodes(new File(filename), "all");
			final Map<String, Tree> outMap = importNeurons(nMap, null, "um");
			return outMap.values().stream().anyMatch(tree -> tree != null && !tree.isEmpty());
		} catch (final FileNotFoundException | IllegalArgumentException | JSONException e) {
			error("Failed to read file: '" + filename + "' (" + e.getMessage() +")");
			return false;
		}
	}

	/**
	 * Imports a reconstruction file (any supported extension).
	 *
	 * @param filePath the absolute path to the file (.Traces, SWC or JSON) to be
	 *                 imported
	 * @return true, if successful
	 */
	public boolean load(final String filePath) {
		final int guessedType = guessTracesFileType(filePath);
		boolean result;
		switch (guessedType) {
			case TRACES_FILE_TYPE_COMPRESSED_XML:
				result = loadCompressedXML(filePath);
				break;
			case TRACES_FILE_TYPE_UNCOMPRESSED_XML:
				result = loadUncompressedXML(filePath);
				break;
			case TRACES_FILE_TYPE_ML_JSON:
				result = loadJSON(filePath);
				break;
			case TRACES_FILE_TYPE_SWC:
				result = importSWC(filePath, false, 0, 0, 0, 1, 1, 1, true);
				break;
			default:
				SNT.warn("guessTracesFileType() return an unknown type" + guessedType);
				return false;
		}
		if (result) {
			final File file = new File(filePath);
			if (getPlugin() != null) getPlugin().prefs.setRecentFile(file);
			if (boundingBox != null) boundingBox.info = file.getName();
		}
		return result;
	}

	synchronized void setPathPointsInVolume(final byte[][] slices,
		final int width, final int height, final int depth)
	{
		setPathPointsInVolume(allPaths, slices, width, height, depth);
	}

	/*
	 * This method will set all the points in array that correspond to points on one
	 * of the paths to 255, leaving everything else as it is. This is useful for
	 * creating stacks that can be used in skeleton analysis plugins that expect a
	 * stack of this kind.
	 */
	synchronized void setPathPointsInVolume(final Collection<Path> paths,
		final byte[][] slices, final int width, final int height, final int depth)
	{
		for (final Path topologyPath : paths) {
			Path p = topologyPath;
			if (topologyPath.getUseFitted()) {
				p = topologyPath.fitted;
			}
			final int n = p.size();

			final ArrayList<Bresenham3D.IntegerPoint> pointsToJoin =
				new ArrayList<>();

			if (p.startJoins != null) {
				final PointInImage s = p.startJoinsPoint;
				final Path sp = p.startJoins;
				final int spi = sp.indexNearestTo(s.x, s.y, s.z);
				pointsToJoin.add(new Bresenham3D.IntegerPoint(sp.getXUnscaled(spi), sp
					.getYUnscaled(spi), sp.getZUnscaled(spi)));
			}

			for (int i = 0; i < n; ++i) {
				pointsToJoin.add(new Bresenham3D.IntegerPoint(p.getXUnscaled(i), p
					.getYUnscaled(i), p.getZUnscaled(i)));
			}

			if (p.endJoins != null) {
				final PointInImage s = p.endJoinsPoint;
				final Path sp = p.endJoins;
				final int spi = sp.indexNearestTo(s.x, s.y, s.z);
				pointsToJoin.add(new Bresenham3D.IntegerPoint(sp.getXUnscaled(spi), sp
					.getYUnscaled(spi), sp.getZUnscaled(spi)));
			}

			Bresenham3D.IntegerPoint previous = null;
			for (final Bresenham3D.IntegerPoint current : pointsToJoin) {
				if (previous == null) {
					previous = current;
					continue;
				}

				/*
				 * If we don't actually need to draw a line, just put a point:
				 */
				if (current.diagonallyAdjacentOrEqual(previous)) {
					slices[current.z][current.y * width + current.x] = (byte) 255;
				}
				else {
					/*
					 * Otherwise draw a line with the 3D version of Bresenham's algorithm:
					 */
					final List<Bresenham3D.IntegerPoint> pointsToDraw = Bresenham3D
						.bresenham3D(previous, current);
					for (final Bresenham3D.IntegerPoint ip : pointsToDraw) {
						try {
							slices[ip.z][ip.y * width + ip.x] = (byte) 255;
						}
						catch (final ArrayIndexOutOfBoundsException ignored) {
							final int x = Math.min(width - 1, Math.max(0, ip.x));
							final int y = Math.min(height - 1, Math.max(0, ip.y));
							final int z = Math.min(depth - 1, Math.max(0, ip.z));
							slices[z][y * width + x] = (byte) 255;
							SNT.warn(String.format(
								"Bresenham3D: Forced out-of-bounds point to [%d][%d * %d + %d]",
								z, y, width, x));
						}
					}
				}

				previous = current;
			}
		}
	}

	synchronized PointInImage nearestJoinPointOnSelectedPaths(final double x,
		final double y, final double z)
	{

		PointInImage result = null;
		double minimumDistanceSquared = Double.MAX_VALUE;

		for (final Path p : allPaths) {

			if (!selectedPathsSet.contains(p)) continue;

			if (0 == p.size()) continue;

			final int i = p.indexNearestTo(x * x_spacing, y * y_spacing, z *
					z_spacing);

			final PointInImage nearestOnPath = p.getNode(i);

			final double distanceSquared = nearestOnPath.distanceSquaredTo(x *
					x_spacing, y * y_spacing, z * z_spacing);

			if (distanceSquared < minimumDistanceSquared) {
				result = nearestOnPath;
				minimumDistanceSquared = distanceSquared;
			}
		}

		return result;
	}

	/**
	 * Returns all the paths.
	 *
	 * @return the paths associated with this PathAndFillManager instance.
	 */
	public ArrayList<Path> getPaths() {
		return allPaths;
	}

	public ArrayList<Path> getPathsFiltered() {
		final ArrayList<Path> paths = new ArrayList<>();
		for (final Path p : getPaths()) {
			if (p == null || p.isFittedVersionOfAnotherPath()) continue;
			paths.add(p);
		}
		return paths;
	}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#transformationStarted(org.scijava.java3d.View)
	 */
	// Methods we need to implement for UniverseListener:
	@Override
	public void transformationStarted(final View view) {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#transformationUpdated(org.scijava.java3d.View)
	 */
	@Override
	public void transformationUpdated(final View view) {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#transformationFinished(org.scijava.java3d.View)
	 */
	@Override
	public void transformationFinished(final View view) {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#contentAdded(ij3d.Content)
	 */
	@Override
	public void contentAdded(final Content c) {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#contentRemoved(ij3d.Content)
	 */
	@Override
	public void contentRemoved(final Content c) {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#contentChanged(ij3d.Content)
	 */
	@Override
	public void contentChanged(final Content c) {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#contentSelected(ij3d.Content)
	 */
	@Override
	public void contentSelected(final Content c) {
		if (c == null) return;
		final String contentName = c.getName();
		final Path selectedPath = getPathFrom3DViewerName(contentName);
		if (plugin != null && selectedPath != null) plugin.selectPath(selectedPath,
			false);
	}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#canvasResized()
	 */
	@Override
	public void canvasResized() {}

	/* (non-Javadoc)
	 * @see ij3d.UniverseListener#universeClosed()
	 */
	@Override
	public void universeClosed() {
		if (plugin != null) {
			plugin.use3DViewer = false;
			plugin.univ = null;
		}
	}
	// ... end of methods for UniverseListener

	public NearPoint nearestPointOnAnyPath(final double x, final double y,
		final double z, final double distanceLimit)
	{
		return nearestPointOnAnyPath(allPaths, new PointInImage(x, y, z), Math.sqrt(
			distanceLimit), false);
	}

	protected List<Path> getAllPathsRenderedInViewPort(
		final TracerCanvas canvas)
	{
		final List<Path> paths = getUnSelectedPathsRenderedInViewPort(canvas);
		paths.addAll(getSelectedPathsRenderedInViewPort(canvas));
		return paths;
	}

	protected List<Path> getSelectedPathsRenderedInViewPort(
		final TracerCanvas canvas)
	{
		final List<Path> paths = new ArrayList<>();
		for (final Path path : allPaths) {
			if (path.isSelected() && path.containsUnscaledNodesInViewPort(canvas))
				paths.add(path);
		}
		return paths;
	}

	protected List<Path> getUnSelectedPathsRenderedInViewPort(
		final TracerCanvas canvas)
	{
		final List<Path> paths = new ArrayList<>();
		for (final Path path : allPaths) {
			if (!path.isSelected() && path.containsUnscaledNodesInViewPort(canvas))
				paths.add(path);
		}
		return paths;
	}

	protected NearPoint nearestPointOnAnyPath(final List<Path> paths,
		final PointInImage pim, final double distanceLimitSquared,
		final boolean unScaledPositions)
	{

		// Order all points in all paths by their Euclidean distance to pim:
		final PriorityQueue<NearPoint> pq = new PriorityQueue<>();

		for (final Path path : paths) {
			if (!path.versionInUse()) continue;
			for (int j = 0; j < path.size(); ++j) {
				pq.add(new NearPoint(pim, path, j, unScaledPositions));
			}
		}

		while (true) {

			final NearPoint np = pq.poll();
			if (np == null) return null;

			/*
			 * Don't bother looking at points that are more than distanceLimit away. Since
			 * we get them in the order closest to furthest away, if we exceed this limit
			 * returned:
			 */
			if (np.distanceToPathPointSquared() > distanceLimitSquared) return null;

			final double distanceToPath = np.distanceToPathNearPoint();
			if (distanceToPath >= 0) return np;
		}
	}

	public Iterator<PointInImage> allPointsIterator() {
		return new AllPointsIterator();
	}

	/*
	 * NB: this returns the number of points in the currently used version of each
	 * path.
	 */
	@SuppressWarnings("unused")
	private int pointsInAllPaths() {
		final Iterator<PointInImage> a = allPointsIterator();
		int points = 0;
		while (a.hasNext()) {
			a.next();
			++points;
		}
		return points;
	}

	/**
	 * For each point in this PathAndFillManager, find the corresponding point on
	 * the other one. If there's no corresponding one, include a null instead. *
	 *
	 * @param other the other PathAndFillManager holding the corresponding Paths
	 * @param maxDistance the distance limit below which the NearPoint is
	 *          considered
	 * @return the cloud of {@link NearPoint} correspondences
	 */
	public List<NearPoint> getCorrespondences(final PathAndFillManager other,
		final double maxDistance)
	{

		final ArrayList<NearPoint> result = new ArrayList<>();

		final Iterator<PointInImage> i = allPointsIterator();
		while (i.hasNext()) {
			final PointInImage p = i.next();
			final NearPoint np = other.nearestPointOnAnyPath(p.x, p.y, p.z,
				maxDistance);
			result.add(np);
		}
		return result;
	}

	/**
	 * Export fills as CSV.
	 *
	 * @param outputFile the output file
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void exportFillsAsCSV(final File outputFile) throws IOException {

		final String[] headers = new String[] { "FillID", "SourcePaths",
			"Threshold", "Metric", "Volume", "LengthUnits" };

		final PrintWriter pw = new PrintWriter(new OutputStreamWriter(
			new FileOutputStream(outputFile.getAbsolutePath()), StandardCharsets.UTF_8));
		final int columns = headers.length;
		for (int c = 0; c < columns; ++c) {
			SNT.csvQuoteAndPrint(pw, headers[c]);
			if (c < (columns - 1)) pw.print(",");
		}
		pw.print("\r\n");
		for (int i = 0; i < allFills.size(); ++i) {
			final Fill f = allFills.get(i);
			SNT.csvQuoteAndPrint(pw, i);
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, f.getSourcePathsStringMachine());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, f.getThreshold());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, f.getMetric());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, f.getVolume());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, f.spacing_units);
			pw.print("\r\n");
		}
		pw.close();
	}

	/**
	 * Output some potentially useful information about all the Paths managed by
	 * this instance as a CSV (comma separated values) file.
	 *
	 * @param outputFile the output file
	 * @throws IOException if data could not be stored
	 */
	public void exportToCSV(final File outputFile) throws IOException {
		// FIXME: also add statistics on volumes of fills and
		// reconstructions...
		final String[] headers = { "PathID", "PathName", "SWCType", "PrimaryPath",
			"PathLength", "PathLengthUnits", "StartsOnPath", "EndsOnPath",
			"ConnectedPathIDs", "ChildPathIDs", "StartX", "StartY", "StartZ", "EndX",
			"EndY", "EndZ", "ApproximateFittedVolume" };

		final Path[] primaryPaths = getPathsStructured();
		final HashSet<Path> h = new HashSet<>();
		Collections.addAll(h, primaryPaths);

		final PrintWriter pw = new PrintWriter(new OutputStreamWriter(
			new FileOutputStream(outputFile.getAbsolutePath()), StandardCharsets.UTF_8));
		final int columns = headers.length;
		for (int c = 0; c < columns; ++c) {
			SNT.csvQuoteAndPrint(pw, headers[c]);
			if (c < (columns - 1)) pw.print(",");
		}
		pw.print("\r\n");
		for (final Path p : allPaths) {
			Path pForLengthAndName = p;
			if (p.getUseFitted()) {
				pForLengthAndName = p.fitted;
			}
			if (p.fittedVersionOf != null) continue;
			SNT.csvQuoteAndPrint(pw, p.getID());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, pForLengthAndName.getName());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, Path.getSWCtypeName(p.getSWCType(), false));
			pw.print(",");
			final boolean primary = h.contains(p);
			SNT.csvQuoteAndPrint(pw, primary);
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, pForLengthAndName.getLength());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, p.spacing_units);
			pw.print(",");
			if (p.startJoins != null) pw.print("" + p.startJoins.getID());
			pw.print(",");
			if (p.endJoins != null) pw.print("" + p.endJoins.getID());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, p.somehowJoinsAsString());
			pw.print(",");
			SNT.csvQuoteAndPrint(pw, p.childrenAsString());
			pw.print(",");

			final double[] startPoint = new double[3];
			final double[] endPoint = new double[3];

			pForLengthAndName.getPointDouble(0, startPoint);
			pForLengthAndName.getPointDouble(pForLengthAndName.size() - 1, endPoint);

			pw.print("" + startPoint[0]);
			pw.print(",");
			pw.print("" + startPoint[1]);
			pw.print(",");
			pw.print("" + startPoint[2]);
			pw.print(",");
			pw.print("" + endPoint[0]);
			pw.print(",");
			pw.print("" + endPoint[1]);
			pw.print(",");
			pw.print("" + endPoint[2]);

			pw.print(",");
			final double fittedVolume = pForLengthAndName.getApproximatedVolume();
			if (fittedVolume >= 0) pw.print(fittedVolume);
			else pw.print("");

			pw.print("\r\n");
			pw.flush();
		}
		pw.close();
	}

	/*
	 * Whatever the state of the paths, update the 3D viewer to make sure that
	 * they're the right colour, the right version (fitted or unfitted) is being
	 * used, whether the line or surface representation is being used, or whether
	 * the path should be displayed at all (it shouldn't if the "Show only selected
	 * paths" option is set.)
	 */

	@SuppressWarnings("deprecation")
	protected void update3DViewerContents() {
		if (plugin != null && !plugin.use3DViewer) return;
		final boolean showOnlySelectedPaths = plugin.isOnlySelectedPathsVisible();
		// Now iterate over all the paths:

		allPaths.stream().forEach(p -> {

			if (p.fittedVersionOf != null) return; // here interpreted as 'continue'

			final boolean selected = p.isSelected();
			final boolean customColor = (p.hasCustomColor &&
				plugin.displayCustomPathColors);
			Color3f color3f;
			if (customColor) color3f = new Color3f(p.getColor());
			else if (selected) color3f = plugin.selectedColor3f;
			else color3f = plugin.deselectedColor3f;

			p.updateContent3D(plugin.univ, // The appropriate 3D universe
				(selected || !showOnlySelectedPaths), // Visible at all?
				plugin.getPaths3DDisplay(), // How to display?
				color3f, plugin.colorImage); // Colour?

			// If path is being rendered with its own custom color, highlight it
			// somehow if is being selected
			if (p.getUseFitted()) p = p.getFitted();
			if (p.content3D != null) p.content3D.setShaded(!(customColor &&
				selected));

		});
	}

	/*
	 * A base class for all the methods we might want to use to transform paths.
	 */

	// Note that this will transform fitted Paths but lose the radii

	public PathAndFillManager transformPaths(final PathTransformer transformation,
		final ImagePlus templateImage, final ImagePlus modelImage)
	{

		double pixelWidth = 1;
		double pixelHeight = 1;
		double pixelDepth = 1;
		String units = "pixels";

		final Calibration templateCalibration = templateImage.getCalibration();
		if (templateCalibration != null) {
			pixelWidth = templateCalibration.pixelWidth;
			pixelHeight = templateCalibration.pixelHeight;
			pixelDepth = templateCalibration.pixelDepth;
			units = templateCalibration.getUnits();
		}

		final PathAndFillManager pafmResult = new PathAndFillManager(pixelWidth,
			pixelHeight, pixelDepth, units);

		final int[] startJoinsIndices = new int[size()];
		final int[] endJoinsIndices = new int[size()];

		final PointInImage[] startJoinsPoints = new PointInImage[size()];
		final PointInImage[] endJoinsPoints = new PointInImage[size()];

		final Path[] addedPaths = new Path[size()];

		int i = 0;
		for (final Path p : allPaths) {

			final Path startJoin = p.getStartJoins();
			if (startJoin == null) {
				startJoinsIndices[i] = -1;
				endJoinsPoints[i] = null;
			}
			else {
				startJoinsIndices[i] = allPaths.indexOf(startJoin);
				final PointInImage transformedPoint = p.getStartJoinsPoint().transform(
					transformation);
				if (transformedPoint.isReal()) startJoinsPoints[i] = transformedPoint;
			}

			final Path endJoin = p.getEndJoins();
			if (endJoin == null) {
				endJoinsIndices[i] = -1;
				endJoinsPoints[i] = null;
			}
			else {
				endJoinsIndices[i] = allPaths.indexOf(endJoin);
				final PointInImage transformedPoint = p.getEndJoinsPoint().transform(
					transformation);
				if (transformedPoint.isReal()) endJoinsPoints[i] = transformedPoint;
			}

			final Path transformedPath = p.transform(transformation, templateImage,
				modelImage);
			if (transformedPath.size() >= 2) {
				addedPaths[i] = transformedPath;
				pafmResult.addPath(transformedPath);
			}

			++i;
		}

		for (i = 0; i < size(); ++i) {
			final int si = startJoinsIndices[i];
			final int ei = endJoinsIndices[i];
			if (addedPaths[i] != null) {
				if (si >= 0 && addedPaths[si] != null && startJoinsPoints[i] != null)
					addedPaths[i].setStartJoin(addedPaths[si], startJoinsPoints[i]);
				if (ei >= 0 && addedPaths[ei] != null && endJoinsPoints[i] != null)
					addedPaths[i].setEndJoin(addedPaths[ei], endJoinsPoints[i]);
			}
		}

		return pafmResult;
	}

	/**
	 * Downsamples alls path using Ramer–Douglas–Peucker simplification.
	 * Downsampling occurs only between branch points and terminal points.
	 *
	 * @param maximumPermittedDistance the maximum permitted distance between
	 *          nodes.
	 */
	public void downsampleAll(final double maximumPermittedDistance) {
		for (final Path p : allPaths) {
			p.downsample(maximumPermittedDistance);
		}
	}

	/**
	 * Gets the SimpleNeuriteTracer instance.
	 *
	 * @return the {@link SimpleNeuriteTracer} instance associated with this
	 *         PathManager (if any)
	 */
	public SimpleNeuriteTracer getPlugin() {
		return plugin;
	}

	public class AllPointsIterator implements Iterator<PointInImage> {

		public AllPointsIterator() {
			numberOfPaths = allPaths.size();
			currentPath = null;
			currentPathIndex = -1;
			currentPointIndex = -1;
		}

		int numberOfPaths;
		// These should all be set to be appropriate to the
		// last point that was returned:
		Path currentPath;
		int currentPathIndex;
		int currentPointIndex;

		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		@Override
		public boolean hasNext() {
			if (currentPath == null || currentPointIndex == currentPath.size() - 1) {
				/*
				 * Find out if there is a non-empty path after this:
				 */
				int tmpPathIndex = currentPathIndex + 1;
				while (tmpPathIndex < numberOfPaths) {
					final Path p = allPaths.get(tmpPathIndex);
					if (p.size() > 0 && p.versionInUse()) return true;
					++tmpPathIndex;
				}
				return false;
			}
			/*
			 * So we know that there's a current path and we're not at the end of it, so
			 * there must be another point:
			 */
			return true;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		@Override
		public PointInImage next() {
			if (currentPath == null || currentPointIndex == currentPath.size() - 1) {
				currentPointIndex = 0;
				/* Move to the next non-empty path: */
				do {
					++currentPathIndex;
					if (currentPathIndex == numberOfPaths)
						throw new java.util.NoSuchElementException();
					currentPath = allPaths.get(currentPathIndex);
				} while (currentPath.size() <= 0 || !currentPath.versionInUse());
			}
			else++currentPointIndex;
			return currentPath.getNode(currentPointIndex);
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		@Override
		public void remove() {
			throw new UnsupportedOperationException(
				"AllPointsIterator does not allow the removal of points");
		}

	}

}

@SuppressWarnings("serial")
class TracesFileFormatException extends SAXException {

	public TracesFileFormatException(final String message) {
		super(message);
	}
}

@SuppressWarnings("serial")
class SWCExportException extends Exception {

	public SWCExportException(final String message) {
		super(message);
	}
}
