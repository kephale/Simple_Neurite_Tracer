/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.DoubleStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.scijava.util.ColorRGB;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.Pipe;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.util.PointInCanvas;
import tracing.util.PointInImage;
import tracing.util.SWCColor;

/**
 * This class represents a traced segment. It has methods to manipulate its
 * points (nodes) with sup-pixel accuracy, including drawing them onto
 * threePane-style canvases, etc.
 **/
public class Path implements Comparable<Path> {

	// http://www.neuronland.org/NLMorphologyConverter/MorphologyFormats/SWC/Spec.html
	/** Flag specifying the SWC type 'undefined'. @see Path#SWC_UNDEFINED_LABEL */
	public static final int SWC_UNDEFINED = 0;
	/** Flag specifying the SWC type 'soma'. @see Path#SWC_SOMA_LABEL */
	public static final int SWC_SOMA = 1;
	/** Flag specifying the SWC type 'axon'. @see Path#SWC_AXON_LABEL */
	public static final int SWC_AXON = 2;
	/** Flag specifying the SWC type '(basal) dendrite'. @see Path#SWC_DENDRITE_LABEL */
	public static final int SWC_DENDRITE = 3;
	/** Flag specifying the SWC type 'apical dendrite'. @see Path#SWC_APICAL_DENDRITE_LABEL */
	public static final int SWC_APICAL_DENDRITE = 4;
	/** Flag specifying the SWC type 'fork point' @see Path#SWC_FORK_POINT_LABEL */
	@Deprecated
	public static final int SWC_FORK_POINT = 5; // redundant
	@Deprecated
	/** Flag specifying the SWC type 'end point'. @see Path#SWC_END_POINT_LABEL */
	public static final int SWC_END_POINT = 6; // redundant
	/** Flag specifying the SWC type 'custom'. @see Path#SWC_CUSTOM_LABEL */
	public static final int SWC_CUSTOM = 7;
	/** String representation of {@link Path#SWC_UNDEFINED} */
	public static final String SWC_UNDEFINED_LABEL = "undefined";
	/** String representation of {@link Path#SWC_SOMA} */
	public static final String SWC_SOMA_LABEL = "soma";
	/** String representation of {@link Path#SWC_AXON} */
	public static final String SWC_AXON_LABEL = "axon";
	/** String representation of {@link Path#SWC_DENDRITE} */
	public static final String SWC_DENDRITE_LABEL = "(basal) dendrite";
	/** String representation of {@link Path#SWC_APICAL_DENDRITE} */
	public static final String SWC_APICAL_DENDRITE_LABEL = "apical dendrite";
	/** String representation of {@link Path#SWC_FORK_POINT} */
	public static final String SWC_FORK_POINT_LABEL = "fork point";
	/** String representation of {@link Path#SWC_END_POINT} */
	public static final String SWC_END_POINT_LABEL = "end point";
	/** String representation of {@link Path#SWC_CUSTOM} */
	public static final String SWC_CUSTOM_LABEL = "custom";

	/*
	 * FIXME: this should be based on distance between points in the path, not a
	 * static number:
	 */
	protected static final int noMoreThanOneEvery = 2;

	/* Path properties */
	// n. of nodes
	private int points;
	// node coordinates
	protected double[] precise_x_positions;
	protected double[] precise_y_positions;
	protected double[] precise_z_positions;
	// radii and tangents
	protected double[] radiuses;
	protected double[] tangents_x;
	protected double[] tangents_y;
	protected double[] tangents_z;
	// numeric properties of nodes (e.g., pixel intensities)
	private float[] values;
	// NB: id should be assigned by PathAndFillManager
	private int id = -1;
	// NB: The leagacy 3D viewer requires always a unique name
	private String name;
	// Reverse Horton-Strahler number of this path
	private int order = 1;
	// The SWC-type flag of this path
	int swcType = SWC_UNDEFINED;
	// is this path selected in the UI?
	private boolean selected;
	// the node being edited when in 'Analysis mode'
	private int editableNodeIndex = -1;
	// the display offset for this Path in a tracing canvas
	protected PointInCanvas canvasOffset = new PointInCanvas(0,0,0);

	/* Spatial calibration definitions */
	protected double x_spacing;
	protected double y_spacing;
	protected double z_spacing;
	protected String spacing_units;

	/* Branching */
	protected Path startJoins;
	protected PointInImage startJoinsPoint = null;
	protected Path endJoins;
	protected PointInImage endJoinsPoint = null;
	// This is a symmetrical relationship, showing
	// all the other paths this one is joined to...
	protected ArrayList<Path> somehowJoins;
	// We sometimes impose a tree structure on the Path
	// graph, which is largely for display purposes. When
	// this is done, we regerated this list. This should
	// always be a subset of 'somehowJoins'...
	protected ArrayList<Path> children;

	/* Fitting (Path refinement) */
	// If this path has a fitted version, this is it
	protected Path fitted;
	// Prefer the fitted flavor of this path
	protected boolean useFitted = false;
	// If this path is a fitted version of another one, this is the original
	protected Path fittedVersionOf;

	/* Color definitions */
	private Color color;
	protected Color3f realColor;
	protected boolean hasCustomColor = false;
	private Color[] nodeColors;

	/* Internal fields */
	private static final int PATH_START = 0;
	private static final int PATH_END = 1;
	private int maxPoints;

	/**
	 * Instantiates a new path.
	 *
	 * @param x_spacing
	 *            Pixel width in spacing_units
	 * @param y_spacing
	 *            Pixel height in spacing_units
	 * @param z_spacing
	 *            Pixel depth in spacing_units
	 * @param spacing_units
	 *            the length unit in physical world units (typically "um").
	 */
	public Path(final double x_spacing, final double y_spacing, final double z_spacing, final String spacing_units) {
		this(x_spacing, y_spacing, z_spacing, spacing_units, 128);
	}

	Path(final double x_spacing, final double y_spacing, final double z_spacing, final String spacing_units,
			final int reserve) {
		this.x_spacing = x_spacing;
		this.y_spacing = y_spacing;
		this.z_spacing = z_spacing;
		this.spacing_units = SNT.getSanitizedUnit(spacing_units);
		points = 0;
		maxPoints = reserve;
		precise_x_positions = new double[maxPoints];
		precise_y_positions = new double[maxPoints];
		precise_z_positions = new double[maxPoints];
		somehowJoins = new ArrayList<>();
		children = new ArrayList<>();
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(final Path o) {
		if (id == o.id)
			return 0;
		if (id < o.id)
			return -1;
		return 1;
	}

	/**
	 * Gets the identifier of this Path
	 *
	 * @return the identifier
	 */
	public int getID() {
		return id;
	}

	protected void setID(final int id) {
		this.id = id;
	}

	/**
	 * Specifies a translation offset when rendering this Path in a
	 * {@link TracerCanvas}. Path coordinates remain unaltered.
	 *
	 * @param canvasOffset
	 *            the x,y,z coordinates (pixel-based) specifying the translation
	 *            offset
	 */
	public void setCanvasOffset(final PointInCanvas canvasOffset) {
		this.canvasOffset = canvasOffset;
	}

	/**
	 * Returns the translation offset used to render this Path in a
	 * {@link TracerCanvas}.
	 *
	 * @return the rendering offset (in pixel coordinates)
	 */
	public PointInCanvas getCanvasOffset() {
		canvasOffset.onPath = this;
		return canvasOffset;
	}

	public Path getStartJoins() {
		return startJoins;
	}

	public PointInImage getStartJoinsPoint() {
		return startJoinsPoint;
	}

	public Path getEndJoins() {
		return endJoins;
	}

	public PointInImage getEndJoinsPoint() {
		return endJoinsPoint;
	}

	/**
	 * Sets this Path's name. Set it to null or {@code ""}, to reset it to the
	 * default.
	 *
	 * @param newName
	 *            the new name.
	 * @see #getName()
	 */
	public void setName(final String newName) {
		this.name = newName;
		getName(); //assign default if newName is null
	}

	/**
	 * Gets this Path's name.
	 *
	 * @return the name. If no name as been set, the default name is returned.
	 * @see #setName(String)
	 */
	public String getName() {
		if (name == null || name.isEmpty())
			name = "Path " + id;
		return name;
	}

	protected static String pathsToIDListString(final ArrayList<Path> a) {
		final StringBuffer s = new StringBuffer("");
		final int n = a.size();
		for (int i = 0; i < n; ++i) {
			s.append(a.get(i).getID());
			if (i < n - 1) {
				s.append(",");
			}
		}
		return s.toString();
	}

	protected String somehowJoinsAsString() {
		return pathsToIDListString(somehowJoins);
	}

	protected String childrenAsString() {
		return pathsToIDListString(children);
	}

	protected void setChildren(final Set<Path> pathsLeft) {
		// Set the children of this path in a breadth first fashion:
		children.clear();
		for (final Path c : somehowJoins) {
			if (pathsLeft.contains(c)) {
				children.add(c);
				pathsLeft.remove(c);
			}
		}
		for (final Path c : children)
			c.setChildren(pathsLeft);
	}

	/**
	 * Gets the length of this Path
	 *
	 * @return the length of this Path
	 */
	public double getLength() {
		double totalLength = 0;
		for (int i = 1; i < points; ++i) {
			final double xdiff = precise_x_positions[i] - precise_x_positions[i - 1];
			final double ydiff = precise_y_positions[i] - precise_y_positions[i - 1];
			final double zdiff = precise_z_positions[i] - precise_z_positions[i - 1];
			totalLength += Math.sqrt(xdiff * xdiff + ydiff * ydiff + zdiff * zdiff);
		}
		return totalLength;
	}

	protected String getRealLengthString() {
		return String.format("%.3f", getLength());
	}

	protected void createCircles() {
		if (tangents_x != null || tangents_y != null || tangents_z != null || radiuses != null)
			throw new IllegalArgumentException(
					"Trying to create circles data arrays when at least one is already there");
		tangents_x = new double[maxPoints];
		tangents_y = new double[maxPoints];
		tangents_z = new double[maxPoints];
		radiuses = new double[maxPoints];
	}

	protected void setIsPrimary(final boolean primary) {
		if (primary) setOrder(1);
	}

	/**
	 * Checks if this Path is root.
	 *
	 * @return true, if is primary (root)
	 */
	public boolean isPrimary() {
		return order == 1;
	}

	/*
	 * We call this if we're going to delete the path represented by this object
	 */
	protected void disconnectFromAll() {
		/*
		 * This path can be connected to other ones either if: - this starts on other -
		 * this ends on other - other starts on this - other ends on this In any of
		 * these cases, we need to also remove this from other's somehowJoins and other
		 * from this's somehowJoins.
		 */
		for (final Path other : somehowJoins) {
			if (other.startJoins != null && other.startJoins == this) {
				other.startJoins = null;
				other.startJoinsPoint = null;
			}
			if (other.endJoins != null && other.endJoins == this) {
				other.endJoins = null;
				other.endJoinsPoint = null;
			}
			final int indexInOtherSomehowJoins = other.somehowJoins.indexOf(this);
			if (indexInOtherSomehowJoins >= 0)
				other.somehowJoins.remove(indexInOtherSomehowJoins);
		}
		somehowJoins.clear();
		startJoins = null;
		startJoinsPoint = null;
		endJoins = null;
		endJoinsPoint = null;
		setOrder(1);
	}

	public void setStartJoin(final Path other, final PointInImage joinPoint) {
		setJoin(PATH_START, other, joinPoint);
	}

	public void setEndJoin(final Path other, final PointInImage joinPoint) {
		setJoin(PATH_END, other, joinPoint);
	}

	/*
	 * This should be the only method that links one path to another
	 */
	protected void setJoin(final int startOrEnd, final Path other, final PointInImage joinPoint) {
		if (other == null) {
			throw new IllegalArgumentException("setJoin should never take a null path");
		}
		if (startOrEnd == PATH_START) {
			// If there was an existing path, that's an error:
			if (startJoins != null)
				throw new IllegalArgumentException("setJoin for START should not replace another join");
			startJoins = other;
			startJoinsPoint = joinPoint;
		} else if (startOrEnd == PATH_END) {
			if (endJoins != null)
				throw new IllegalArgumentException("setJoin for END should not replace another join");
			endJoins = other;
			endJoinsPoint = joinPoint;
		} else {
			SNT.log("BUG: unknown first parameter to setJoin");
		}
		// Also update the somehowJoins list:
		if (somehowJoins.indexOf(other) < 0) {
			somehowJoins.add(other);
		}
		if (other.somehowJoins.indexOf(this) < 0) {
			other.somehowJoins.add(this);
		}
		// update order
		setOrder(other.order + 1);
	}

	public void unsetStartJoin() {
		unsetJoin(PATH_START);
	}

	public void unsetEndJoin() {
		unsetJoin(PATH_END);
	}

	void unsetJoin(final int startOrEnd) {
		Path other;
		Path leaveAloneJoin;
		if (startOrEnd == PATH_START) {
			other = startJoins;
			leaveAloneJoin = endJoins;
		} else {
			other = endJoins;
			leaveAloneJoin = startJoins;
		}
		if (other == null) {
			throw new IllegalArgumentException("Don't call unsetJoin if the other Path is already null");
		}
		if (!(other.startJoins == this || other.endJoins == this || leaveAloneJoin == other)) {
			somehowJoins.remove(other);
			other.somehowJoins.remove(this);
		}
		if (startOrEnd == PATH_START) {
			startJoins = null;
			startJoinsPoint = null;
		} else {
			endJoins = null;
			endJoinsPoint = null;
		}
		setOrder(-1);
	}

	protected double getMinimumSeparation() {
		return Math.min(Math.abs(x_spacing), Math.min(Math.abs(y_spacing), Math.abs(z_spacing)));
	}

	/**
	 * Returns the number of nodes of this path
	 *
	 * @return the size, i.e., number of nodes
	 */
	public int size() {
		return points;
	}

	public void getPointDouble(final int i, final double[] p) {

		if ((i < 0) || i >= size()) {
			throw new RuntimeException("BUG: getPointDouble was asked for an out-of-range point: " + i);
		}

		p[0] = precise_x_positions[i];
		p[1] = precise_y_positions[i];
		p[2] = precise_z_positions[i];
	}

	public PointInImage getPointInImage(final int node) {
		if ((node < 0) || node >= size()) {
			throw new IllegalArgumentException("getPointInImage() was asked for an out-of-range point: " + node);
		}
		final PointInImage result = new PointInImage(precise_x_positions[node], precise_y_positions[node],
				precise_z_positions[node]);
		result.onPath = this;
		return result;
	}

	protected PointInCanvas getPointInCanvas(final int node) {
		final PointInCanvas result = new PointInCanvas(getXUnscaledDouble(node), getYUnscaledDouble(node),
				getZUnscaledDouble(node));
		result.onPath = this;
		return result;
	}

	protected boolean containsUnscaledNodesInViewPort(final TracerCanvas canvas) {
		final Rectangle rect = canvas.getSrcRect();
		final double minX = rect.getMinX();
		final double minY = rect.getMinY();
		final double maxX = rect.getMaxX();
		final double maxY = rect.getMaxY();
		for (int i = 0; i< size(); i++) {
			final PointInCanvas node = getPointInCanvas(i);
			if (node.x >= minX && node.y >= minY && node.x <= maxX && node.y <= maxY) return true;
		}
		return false;
	}

	/**
	 * Check whether this Path contains the specified point
	 * 
	 * @param pim
	 *            the {@link PointInImage} node
	 * @return true, if successful
	 */
	public boolean contains(final PointInImage pim) {
		if (pim.onPath != null) return pim.onPath == this;
		return (DoubleStream.of(precise_x_positions).anyMatch(x -> x == pim.x)
				&& DoubleStream.of(precise_y_positions).anyMatch(y -> y == pim.y)
				&& DoubleStream.of(precise_z_positions).anyMatch(z -> z == pim.z));
	}

	/**
	 * Inserts a node at a specified position.
	 *
	 * @param index
	 *            the (zero-based) index of the position of the new node
	 * @param point
	 *            the node to be inserted
	 * @throws IllegalArgumentException
	 *             if index is out-of-range
	 */
	public void addNode(final int index, final PointInImage point) {
		if (index < 0 || index > size())
			throw new IllegalArgumentException("addNode() asked for an out-of-range point: " + index);
		// FIXME: This all would be much easier if we were using Collections/Lists
		precise_x_positions = ArrayUtils.insert(index, precise_x_positions, point.x);
		precise_y_positions = ArrayUtils.insert(index, precise_y_positions, point.y);
		precise_z_positions = ArrayUtils.insert(index, precise_z_positions, point.z);
	}

	/**
	 * Removes the specified node if this path has at least two nodes. Does nothing
	 * if this is a single point path.
	 *
	 * @param index
	 *            the zero-based index of the node to be removed
	 * @throws IllegalArgumentException
	 *             if index is out-of-range
	 */
	public void removeNode(final int index) {
		if (points == 1)
			return;
		if (index < 0 || index >= points)
			throw new IllegalArgumentException("removeNode() asked for an out-of-range point: " + index);
		// FIXME: This all would be much easier if we were using Collections/Lists
		final PointInImage p = getPointInImage(index);
		precise_x_positions = ArrayUtils.remove(precise_x_positions, index);
		precise_y_positions = ArrayUtils.remove(precise_y_positions, index);
		precise_z_positions = ArrayUtils.remove(precise_z_positions, index);
		points -= 1;
		if (p.equals(startJoinsPoint))
			startJoinsPoint = getPointInImage(0);
		if (p.equals(endJoinsPoint) && points > 0)
			endJoinsPoint = getPointInImage(points - 1);
	}

	/**
	 * Assigns a new location to the specified node.
	 *
	 * @param index
	 *            the zero-based index of the node to be modified
	 * @param destination
	 *            the new node location
	 * @throws IllegalArgumentException
	 *             if index is out-of-range
	 */
	public void moveNode(final int index, final PointInImage destination) {
		if (index < 0 || index >= size())
			throw new IllegalArgumentException("moveNode() asked for an out-of-range point: " + index);
		precise_x_positions[index] = destination.x;
		precise_y_positions[index] = destination.y;
		precise_z_positions[index] = destination.z;
	}

	/**
	 * Gets the first node index associated with the specified image coordinates.
	 * Returns -1 if no such node was found.
	 *
	 * @param pim
	 *            the image position (calibrated coordinates)
	 * @return the index of the first node occurrence or -1 if there is no such
	 *         occurrence
	 */
	public int getNodeIndex(final PointInImage pim) {
		for (int i = 0; i < points; ++i) {
			if (Math.abs(precise_x_positions[i] - pim.x) < x_spacing
					&& Math.abs(precise_y_positions[i] - pim.y) < y_spacing
					&& Math.abs(precise_z_positions[i] - pim.z) < z_spacing) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gets the index of the closest node associated with the specified world
	 * coordinates.
	 *
	 * @param x
	 *            the x-coordinates (spatially calibrated image units)
	 * @param y
	 *            the y-coordinates (spatially calibrated image units)
	 * @param z
	 *            the z-coordinates (spatially calibrated image units)
	 * @param within
	 *            sets the search sensitivity. E.g., Setting it to Double.MAX_VALUE
	 *            (or the image's largest dimension) will always return a valid
	 *            index.
	 * @return the index of the closest node to the specified coordinates. Returns
	 *         -1 if no such node was found.
	 */
	public int indexNearestTo(final double x, final double y, final double z, final double within) {

		if (size() < 1)
			throw new IllegalArgumentException("indexNearestTo called on a Path of size() = 0");

		double minimumDistanceSquared = within * within;
		int indexOfMinimum = -1;

		for (int i = 0; i < size(); ++i) {

			final double diff_x = x - precise_x_positions[i];
			final double diff_y = y - precise_y_positions[i];
			final double diff_z = z - precise_z_positions[i];

			final double thisDistanceSquared = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z;

			if (thisDistanceSquared < minimumDistanceSquared) {
				indexOfMinimum = i;
				minimumDistanceSquared = thisDistanceSquared;
			}
		}

		return indexOfMinimum;
	}

	protected int indexNearestToCanvasPosition2D(final double xInCanvas, final double yInCanvas, final double withinPixels) {
		double minimumDistanceSquared = withinPixels * withinPixels;
		int indexOfMinimum = -1;
		for (int i = 0; i < size(); ++i) {
			final double diff_x = xInCanvas - getXUnscaledDouble(i);
			final double diff_y = yInCanvas - getYUnscaledDouble(i);
			final double thisDistanceSquared = diff_x * diff_x + diff_y * diff_y;
			if (thisDistanceSquared < minimumDistanceSquared) {
				indexOfMinimum = i;
				minimumDistanceSquared = thisDistanceSquared;
			}
		}
		return indexOfMinimum;
	}

	protected int indexNearestTo(final double x, final double y, final double z) {
		return indexNearestTo(x, y, z, Double.MAX_VALUE);
	}

	/**
	 * Gets the position of the node tagged as 'editable', if any.
	 *
	 * @return the index of the point currently tagged as editable, or -1 if no such
	 *         point exists
	 */
	public int getEditableNodeIndex() {
		return editableNodeIndex;
	}

	/**
	 * Tags the specified point position as 'editable'.
	 *
	 * @param index
	 *            the index of the point to be tagged. Set it to -1 to for no
	 *            tagging
	 */
	public void setEditableNode(final int index) {
		this.editableNodeIndex = index;
	}

	protected boolean isBeingEdited() {
		return editableNodeIndex != -1;
	}

	protected void stopBeingEdited() {
		editableNodeIndex = -1;
	}

	public int getXUnscaled(final int i) {
		return (int) Math.round(getXUnscaledDouble(i));
	}

	public int getYUnscaled(final int i) {
		return (int) Math.round(getYUnscaledDouble(i));
	}

	public int getZUnscaled(final int i) {
		return (int) Math.round(getZUnscaledDouble(i));
	}

	public double getXUnscaledDouble(final int i) {
//		if ((i < 0) || i >= size())
//			throw new IllegalArgumentException("getXUnscaled was asked for an out-of-range point: " + i);
		return precise_x_positions[i] / x_spacing + canvasOffset.x;
	}

	public double getYUnscaledDouble(final int i) {
//		if ((i < 0) || i >= size())
//			throw new IllegalArgumentException("getYUnscaled was asked for an out-of-range point: " + i);
		return precise_y_positions[i] / y_spacing + canvasOffset.y;
	}

	public double getZUnscaledDouble(final int i) {
//		if ((i < 0) || i >= size())
//			throw new IllegalArgumentException("getZUnscaled was asked for an out-of-range point: " + i);
		return precise_z_positions[i] / z_spacing + canvasOffset.z;
	}

	/*
	 * FIXME:
	 *
	 * @Override public Path clone() {
	 *
	 * Path result = new Path( points );
	 *
	 * System.arraycopy( x_positions, 0, result.x_positions, 0, points );
	 * System.arraycopy( y_positions, 0, result.y_positions, 0, points );
	 * System.arraycopy( z_positions, 0, result.z_positions, 0, points );
	 * result.points = points; result.startJoins = startJoins;
	 * result.startJoinsIndex = startJoinsIndex; result.endJoins = endJoins;
	 * result.endJoinsIndex = endJoinsIndex;
	 *
	 * if( radiuses != null ) { this.radiuses = new double[radiuses.length];
	 * System.arraycopy( radiuses, 0, result.radiuses, 0, radiuses.length ); } if(
	 * tangents_x != null ) { this.tangents_x = new double[tangents_x.length];
	 * System.arraycopy( tangents_x, 0, result.tangents_x, 0, tangents_x.length ); }
	 * if( tangents_y != null ) { this.tangents_y = new double[tangents_y.length];
	 * System.arraycopy( tangents_y, 0, result.tangents_y, 0, tangents_y.length ); }
	 * if( tangents_z != null ) { this.tangents_z = new double[tangents_z.length];
	 * System.arraycopy( tangents_z, 0, result.tangents_z, 0, tangents_z.length ); }
	 *
	 * return result; }
	 */

	/**
	 * Gets the spatial calibration of this Path.
	 *
	 * @return the calibration details associated with this Path
	 */
	public Calibration getCalibration() {
		final Calibration cal = new Calibration();
		cal.setUnit(spacing_units);
		cal.pixelWidth = x_spacing;
		cal.pixelHeight = y_spacing;
		cal.pixelDepth = z_spacing;
		return cal;
	}

	protected PointInImage lastPoint() {
		if (points < 1)
			return null;
		else
			return new PointInImage(precise_x_positions[points - 1], precise_y_positions[points - 1],
					precise_z_positions[points - 1]);
	}

	private void expandTo(final int newMaxPoints) {

		final double[] new_precise_x_positions = new double[newMaxPoints];
		final double[] new_precise_y_positions = new double[newMaxPoints];
		final double[] new_precise_z_positions = new double[newMaxPoints];
		System.arraycopy(precise_x_positions, 0, new_precise_x_positions, 0, points);
		System.arraycopy(precise_y_positions, 0, new_precise_y_positions, 0, points);
		System.arraycopy(precise_z_positions, 0, new_precise_z_positions, 0, points);
		precise_x_positions = new_precise_x_positions;
		precise_y_positions = new_precise_y_positions;
		precise_z_positions = new_precise_z_positions;
		if (hasRadii()) {
			final double[] new_tangents_x = new double[newMaxPoints];
			final double[] new_tangents_y = new double[newMaxPoints];
			final double[] new_tangents_z = new double[newMaxPoints];
			final double[] new_radiuses = new double[newMaxPoints];
			System.arraycopy(tangents_x, 0, new_tangents_x, 0, points);
			System.arraycopy(tangents_y, 0, new_tangents_y, 0, points);
			System.arraycopy(tangents_z, 0, new_tangents_z, 0, points);
			System.arraycopy(radiuses, 0, new_radiuses, 0, points);
			tangents_x = new_tangents_x;
			tangents_y = new_tangents_y;
			tangents_z = new_tangents_z;
			radiuses = new_radiuses;
		}
		maxPoints = newMaxPoints;
	}

	protected void add(final Path other) {

		if (other == null) {
			SNT.warn("BUG: Trying to add null Path");
			return;
		}

		// If we're trying to add a path with circles to one
		// that previously had none, add circles to the
		// previous one, and carry on:

		if (other.hasRadii() && !hasRadii()) {
			createCircles();
			final double defaultRadius = getMinimumSeparation() * 2;
			for (int i = 0; i < points; ++i)
				radiuses[i] = defaultRadius;
		}

		if (maxPoints < (points + other.points)) {
			expandTo(points + other.points);
		}

		int toSkip = 0;

		/*
		 * We may want to skip some points at the beginning of the next path if they're
		 * the same as the last point on this path:
		 */
		if (points > 0) {
			final double last_x = precise_x_positions[points - 1];
			final double last_y = precise_y_positions[points - 1];
			final double last_z = precise_z_positions[points - 1];
			while ((other.precise_x_positions[toSkip] == last_x) && (other.precise_y_positions[toSkip] == last_y)
					&& (other.precise_z_positions[toSkip] == last_z)) {
				++toSkip;
			}
		}

		System.arraycopy(other.precise_x_positions, toSkip, precise_x_positions, points, other.points - toSkip);
		System.arraycopy(other.precise_y_positions, toSkip, precise_y_positions, points, other.points - toSkip);
		System.arraycopy(other.precise_z_positions, toSkip, precise_z_positions, points, other.points - toSkip);

		if (hasRadii()) {
			System.arraycopy(other.radiuses, toSkip, radiuses, points, other.points - toSkip);
		}

		if (endJoins != null)
			throw new IllegalArgumentException("BUG: we should never be adding to a path that already endJoins");

		if (other.endJoins != null) {
			setEndJoin(other.endJoins, other.endJoinsPoint);
			other.disconnectFromAll();
		}

		points = points + (other.points - toSkip);

		if (hasRadii()) {
			setGuessedTangents(2);
		}
	}

	protected void unsetPrimaryForConnected(final HashSet<Path> pathsExplored) {
		for (final Path p : somehowJoins) {
			if (pathsExplored.contains(p))
				continue;
			p.setIsPrimary(false);
			pathsExplored.add(p);
			p.unsetPrimaryForConnected(pathsExplored);
		}
	}

	protected Path reversed() {
		final Path c = new Path(x_spacing, y_spacing, z_spacing, spacing_units, points);
		c.points = points;
		for (int i = 0; i < points; ++i) {
			c.precise_x_positions[i] = precise_x_positions[(points - 1) - i];
			c.precise_y_positions[i] = precise_y_positions[(points - 1) - i];
			c.precise_z_positions[i] = precise_z_positions[(points - 1) - i];
		}
		return c;
	}

	public void addPointDouble(final double x, final double y, final double z) {
		if (points >= maxPoints) {
			final int newReserved = (int) (maxPoints * 1.2 + 1);
			expandTo(newReserved);
		}
		precise_x_positions[points] = x;
		precise_y_positions[points] = y;
		precise_z_positions[points++] = z;
	}

	public void drawPathAsPoints(final TracerCanvas canvas, final Graphics2D g, final java.awt.Color c, final int plane,
			final boolean highContrast, final boolean drawDiameter) {
		drawPathAsPoints(canvas, g, c, plane, highContrast, drawDiameter, 0, -1);
	}

	protected void drawPathAsPoints(final TracerCanvas canvas, final Graphics2D g, final java.awt.Color c,
			final int plane, final boolean drawDiameter, final int slice, final int either_side) {
		drawPathAsPoints(canvas, g, c, plane, false, drawDiameter, slice, either_side);
	}

	protected void drawPathAsPoints(final Graphics2D g2, final TracerCanvas canvas, final SimpleNeuriteTracer snt) {
		final boolean customColor = (hasCustomColor && snt.displayCustomPathColors);
		Color color = snt.deselectedColor;
		if (isSelected() && !customColor)
			color = snt.selectedColor;
		else if (customColor)
			color = getColor();
		final int sliceZeroIndexed = canvas.getImage().getZ() - 1;
		int eitherSideParameter = canvas.eitherSide;
		if (!canvas.just_near_slices)
			eitherSideParameter = -1;
		drawPathAsPoints(canvas, g2, color, canvas.getPlane(), customColor, snt.drawDiametersXY, sliceZeroIndexed,
				eitherSideParameter);
	}

	public void drawPathAsPoints(final TracerCanvas canvas, final Graphics2D g2, final java.awt.Color c,
			final int plane, final boolean highContrast, boolean drawDiameter, final int slice, final int either_side) {

		g2.setColor(c);
		int startIndexOfLastDrawnLine = -1;

		if (!hasRadii())
			drawDiameter = false;

		for (int i = 0; i < points; ++i) {

			double previous_x_on_screen = Integer.MIN_VALUE;
			double previous_y_on_screen = Integer.MIN_VALUE;
			double next_x_on_screen = Integer.MIN_VALUE;
			double next_y_on_screen = Integer.MIN_VALUE;
			final boolean notFirstPoint = i > 0;
			final boolean notLastPoint = i < points - 1;
			int slice_of_point = Integer.MIN_VALUE;

			switch (plane) {
			case MultiDThreePanes.XY_PLANE:
				if (notFirstPoint) {
					previous_x_on_screen = canvas.myScreenXDprecise(getXUnscaledDouble(i - 1));
					previous_y_on_screen = canvas.myScreenYDprecise(getXUnscaledDouble(i - 1));
				}
				if (notLastPoint) {
					next_x_on_screen = canvas.myScreenXDprecise(getXUnscaledDouble(i + 1));
					next_y_on_screen = canvas.myScreenYDprecise(getYUnscaledDouble(i + 1));
				}
				slice_of_point = getZUnscaled(i);
				break;
			case MultiDThreePanes.XZ_PLANE:
				if (notFirstPoint) {
					previous_x_on_screen = canvas.myScreenXDprecise(getXUnscaledDouble(i - 1));
					previous_y_on_screen = canvas.myScreenYDprecise(getZUnscaledDouble(i - 1));
				}
				if (notLastPoint) {
					next_x_on_screen = canvas.myScreenXDprecise(getXUnscaledDouble(i + 1));
					next_y_on_screen = canvas.myScreenYDprecise(getZUnscaledDouble(i + 1));
				}
				slice_of_point = getYUnscaled(i);
				break;
			case MultiDThreePanes.ZY_PLANE:
				if (notFirstPoint) {
					previous_x_on_screen = canvas.myScreenXDprecise(getZUnscaledDouble(i - 1));
					previous_y_on_screen = canvas.myScreenYDprecise(getYUnscaledDouble(i - 1));
				}
				if (notLastPoint) {
					next_x_on_screen = canvas.myScreenXDprecise(getZUnscaledDouble(i + 1));
					next_y_on_screen = canvas.myScreenYDprecise(getYUnscaledDouble(i + 1));
				}
				slice_of_point = getXUnscaled(i);
				break;
			default:
				throw new IllegalArgumentException("BUG: Unknown plane! (" + plane + ")");
			}

			final PathNode pn = new PathNode(this, i, canvas);
			final boolean outOfDepthBounds = (either_side >= 0) && (Math.abs(slice_of_point - slice) > either_side);
			g2.setColor(SWCColor.alphaColor(c, (outOfDepthBounds) ? 50 : 100));

			// If there was a previous point in this path, draw a line from there to here:
			if (notFirstPoint) {
				// Don't redraw the line if we drew it the previous time, though:
				if (startIndexOfLastDrawnLine != i - 1) {
					g2.draw(new Line2D.Double(previous_x_on_screen, previous_y_on_screen, pn.x, pn.y));
					startIndexOfLastDrawnLine = i - 1;
				}
			}

			// If there's a next point in this path, draw a line from here to there:
			if (notLastPoint) {
				g2.draw(new Line2D.Double(pn.x, pn.y, next_x_on_screen, next_y_on_screen));
				startIndexOfLastDrawnLine = i;
			}

			if (outOfDepthBounds)
				continue; // draw nothing more for points out-of-bounds

			// If we've been asked to draw the diameters, just do it in XY
			if (drawDiameter && plane == MultiDThreePanes.XY_PLANE) {

				// Cross the tangents with a unit z vector:
				final double n_x = 0;
				final double n_y = 0;
				final double n_z = 1;
				final double t_x = tangents_x[i];
				final double t_y = tangents_y[i];
				final double t_z = tangents_z[i];
				final double cross_x = n_y * t_z - n_z * t_y;
				final double cross_y = n_z * t_x - n_x * t_z;
				// double cross_z = n_x * t_y - n_y * t_x;

				final double sizeInPlane = Math.sqrt(cross_x * cross_x + cross_y * cross_y);
				final double normalized_cross_x = cross_x / sizeInPlane;
				final double normalized_cross_y = cross_y / sizeInPlane;
				final double zdiff = Math.abs((slice - slice_of_point) * z_spacing);
				final double realRadius = radiuses[i];

				if (either_side < 0 || zdiff <= realRadius) {

					double effective_radius;
					if (either_side < 0)
						effective_radius = realRadius;
					else
						effective_radius = Math.sqrt(realRadius * realRadius - zdiff * zdiff);

					final double left_x = precise_x_positions[i] + normalized_cross_x * effective_radius;
					final double left_y = precise_y_positions[i] + normalized_cross_y * effective_radius;
					final double right_x = precise_x_positions[i] - normalized_cross_x * effective_radius;
					final double right_y = precise_y_positions[i] - normalized_cross_y * effective_radius;

					final double left_x_on_screen = canvas.myScreenXDprecise(canvasOffset.x + left_x / x_spacing);
					final double left_y_on_screen = canvas.myScreenYDprecise(canvasOffset.y + left_y / y_spacing);
					final double right_x_on_screen = canvas.myScreenXDprecise(canvasOffset.x + right_x / x_spacing);
					final double right_y_on_screen = canvas.myScreenYDprecise(canvasOffset.y + right_y / y_spacing);

					final double x_on_screen = canvas.myScreenXDprecise(canvasOffset.x + precise_x_positions[i] / x_spacing);
					final double y_on_screen = canvas.myScreenYDprecise(canvasOffset.y + precise_y_positions[i] / y_spacing);

					g2.draw(new Line2D.Double(x_on_screen, y_on_screen, left_x_on_screen, left_y_on_screen));
					g2.draw(new Line2D.Double(x_on_screen, y_on_screen, right_x_on_screen, right_y_on_screen));
				}

			}

			// Draw node
			pn.setEditable(getEditableNodeIndex() == i);
			pn.draw(g2, c);
			// g2.setColor(c); // reset color transparencies. Not really needed
		}

	}

	/**
	 * Sets the node colors.
	 *
	 * @param colors
	 *            the colors used to render the nodes of this. If null (the default)
	 *            all nodes are rendered using the Path color.
	 */
	public void setNodeColors(final Color[] colors) {
		if (colors != null && colors.length != size()) {
			throw new IllegalArgumentException("colors array must have as many elements as nodes");
		}
		nodeColors = colors;
	}

	/**
	 * Gets the node colors.
	 *
	 * @return the colors used to render the nodes of this path, or null if nodes
	 *         are rendered using the Path color
	 */
	public Color[] getNodeColors() {
		return nodeColors;
	}

	/**
	 * Gets the node color.
	 *
	 * @param pos
	 *            the node position
	 * @return the node color, or null if 
	 */
	public Color getNodeColor(final int pos) {
		return (nodeColors == null) ? null : nodeColors[pos];
	}

	/**
	 * Sets the node color.
	 *
	 * @param color the node color
	 * @param pos   the node position
	 */
	public void setNodeColor(final Color color, final int pos) {
		if(nodeColors == null) nodeColors = new Color[size()];
		nodeColors[pos] = color;
	}

	public void setValue(final float value, final int pos) {
		if(values == null) values = new float[size()];
		values[pos] = value;
	}

	public float getValue(final int pos) {
		return (values == null) ? null : values[pos];
	}

	/**
	 * Gets the color of this Path
	 *
	 * @return the color, or null if no color has been assigned to this Path
	 * @see #hasCustomColor
	 * @see #hasNodeColors()
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * Sets this path color.
	 *
	 * @param color
	 *            the path color. Set it to null, to have SNT rendered using default
	 *            settings.
	 */
	public void setColor(final Color color) {
		this.color = color;
		hasCustomColor = color != null;
		if (fitted != null)
			fitted.setColor(color);
	}

	/**
	 * Sets this path color.
	 *
	 * @param colorRGB
	 *            the path color. Set it to null, to have SNT rendered using default
	 *            settings.
	 */
	public void setColorRGB(final ColorRGB colorRGB) {
		setColor((colorRGB == null) ? null : new Color(colorRGB.getARGB()));
	}

	/**
	 * Assesses whether a custom color has been assigned to this Path, or its nodes
	 * have been assigned an array of colors
	 *
	 *
	 * @return true, if successful
	 * @see #hasNodeColors()
	 */
	public boolean hasCustomColor() {
		return (hasCustomColor && color != null) || hasNodeColors();
	}

	/**
	 * Assesses whether the nodes of this path have been assigned an array of colors
	 *
	 * @return true, if successful
	 * @see #getNodeColors()
	 */
	public boolean hasNodeColors() {
		return nodeColors != null;
	}

	/**
	 * Gets the default SWC colors used by SNT.
	 *
	 * @param swcType
	 *            the SEC type (e.g., {@link Path#SWC_AXON},
	 *            {@link Path#SWC_DENDRITE}, etc.)
	 * @return the SWC color
	 */
	public static Color getSWCcolor(final int swcType) {
		switch (swcType) {
		case Path.SWC_SOMA:
			return Color.BLUE;
		case Path.SWC_DENDRITE:
			return Color.GREEN;
		case Path.SWC_APICAL_DENDRITE:
			return Color.CYAN;
		case Path.SWC_AXON:
			return Color.RED;
		case Path.SWC_FORK_POINT:
			return Color.ORANGE;
		case Path.SWC_END_POINT:
			return Color.PINK;
		case Path.SWC_CUSTOM:
			return Color.YELLOW;
		case Path.SWC_UNDEFINED:
		default:
			return SimpleNeuriteTracer.DEFAULT_DESELECTED_COLOR;
		}
	}

	/**
	 * Checks if is fitted version of another path.
	 *
	 * @return true, if is fitted version of another path
	 */
	public boolean isFittedVersionOfAnotherPath() {
		return fittedVersionOf != null;
	}

	protected void setFitted(final Path p) {
		if (fitted != null && p != null) {
			throw new IllegalArgumentException("BUG: Trying to set a fitted path when there already is one...");
		}
		fitted = p;
		if (p == null) {
			setUseFitted(false);
		} else {
			p.fittedVersionOf = this;
		}
	}

	public void setUseFitted(final boolean useFitted) {
		if (useFitted && fitted == null)
			throw new IllegalArgumentException("setUseFitted(true) called, but 'fitted' member was null");
		this.useFitted = useFitted;
	}

	public boolean getUseFitted() {
		return useFitted;
	}

	public Path getFitted() {
		return fitted;
	}

	protected void setGuessedTangents(final int pointsEitherSide) {
		if (tangents_x == null || tangents_y == null || tangents_z == null)
			throw new IllegalArgumentException("BUG: setGuessedTangents called with one of the tangent arrays null");
		final double[] tangent = new double[3];
		for (int i = 0; i < points; ++i) {
			getTangent(i, pointsEitherSide, tangent);
			tangents_x[i] = tangent[0];
			tangents_y[i] = tangent[1];
			tangents_z[i] = tangent[2];
		}
	}

	protected void getTangent(final int i, final int pointsEitherSide, final double[] result) {
		int min_index = i - pointsEitherSide;
		if (min_index < 0)
			min_index = 0;

		int max_index = i + pointsEitherSide;
		if (max_index >= points)
			max_index = points - 1;

		result[0] = precise_x_positions[max_index] - precise_x_positions[min_index];
		result[1] = precise_y_positions[max_index] - precise_y_positions[min_index];
		result[2] = precise_z_positions[max_index] - precise_z_positions[min_index];
	}

	/**
	 * Gets the list of string representations of non-redundant SWC types (i.e.,
	 * excluding {@link Path#SWC_FORK_POINT_LABEL}, and
	 * {@link Path#SWC_FORK_POINT_LABEL}
	 * 
	 * @return the list of SWC type names
	 */
	public static ArrayList<String> getSWCtypeNames() {
		final ArrayList<String> swcTypes = new ArrayList<>();
		swcTypes.add(SWC_UNDEFINED_LABEL);
		swcTypes.add(SWC_SOMA_LABEL);
		swcTypes.add(SWC_AXON_LABEL);
		swcTypes.add(SWC_DENDRITE_LABEL);
		swcTypes.add(SWC_APICAL_DENDRITE_LABEL);
		//swcTypes.add(SWC_FORK_POINT_LABEL);
		//swcTypes.add(SWC_END_POINT_LABEL);
		swcTypes.add(SWC_CUSTOM_LABEL);
		return swcTypes;
	}

	/**
	 * Gets the list of non-redundant SWC types (i.e., excluding the redundant types
	 * {@link Path#SWC_FORK_POINT_LABEL}, and {@link Path#SWC_FORK_POINT_LABEL}
	 * 
	 * @return the list of SWC type flags
	 */
	public static ArrayList<Integer> getSWCtypes() {
		final ArrayList<Integer> swcTypes = new ArrayList<>();
		swcTypes.add(SWC_UNDEFINED);
		swcTypes.add(SWC_SOMA);
		swcTypes.add(SWC_AXON);
		swcTypes.add(SWC_DENDRITE);
		swcTypes.add(SWC_APICAL_DENDRITE);
		//swcTypes.add(SWC_FORK_POINT);
		//swcTypes.add(SWC_END_POINT);
		swcTypes.add(SWC_CUSTOM);
		return swcTypes;
	}

	/**
	 * Gets the SWC type label associated with the specified type flag. SNT follows
	 * the specification detailed at <a href=
	 * "http://www.neuronland.org/NLMorphologyConverter/MorphologyFormats/SWC/Spec.html">neuronland</a>
	 *
	 * @param type            the SWC type flag
	 * @param capitalized whether output String should be capitalized
	 * @return the respective label, or {@link Path#SWC_UNDEFINED_LABEL} if flag was
	 *         not recognized
	 */
	public static String getSWCtypeName(final int type, final boolean capitalized) {
		String typeName;
		switch (type) {
		case SWC_UNDEFINED:
			typeName = SWC_UNDEFINED_LABEL;
			break;
		case SWC_SOMA:
			typeName = SWC_SOMA_LABEL;
			break;
		case SWC_AXON:
			typeName = SWC_AXON_LABEL;
			break;
		case SWC_DENDRITE:
			typeName = SWC_DENDRITE_LABEL;
			break;
		case SWC_APICAL_DENDRITE:
			typeName = SWC_APICAL_DENDRITE_LABEL;
			break;
		case SWC_FORK_POINT:
			typeName = SWC_FORK_POINT_LABEL;
			break;
		case SWC_END_POINT:
			typeName = SWC_END_POINT_LABEL;
			break;
		case SWC_CUSTOM:
			typeName = SWC_CUSTOM_LABEL;
			break;
		default:
			typeName = SWC_UNDEFINED_LABEL;
			break;
		}
		if (!capitalized) return typeName;

		final char[] buffer = typeName.toCharArray();
		boolean capitalizeNext = true;
		for (int i = 0; i < buffer.length; i++) {
			final char ch = buffer[i];
			if (Character.isWhitespace(ch) || !Character.isLetter(ch)) {
				capitalizeNext = true;
			} else if (capitalizeNext) {
				buffer[i] = Character.toTitleCase(ch);
				capitalizeNext = false;
			}
		}
		return new String(buffer);
	}

	/**
	 * Gets the path mean radius.
	 *
	 * @return the average radius of the path, or zero if path has no defined
	 *         thickness
	 * @see #hasRadii()
	 */
	public double getMeanRadius() {
		return (hasRadii()) ? StatUtils.mean(radiuses) : 0;
	}

	/**
	 * Gets the radius of the specified node.
	 *
	 * @param pos
	 *            the node position
	 * @return the radius at the specified position, or zero if path has no defined
	 *         thickness
	 */
	public double getNodeRadius(final int pos) {
		if (radiuses == null)
			return 0;
		if ((pos < 0) || pos >= size()) {
			throw new IllegalArgumentException("getNodeRadius() was asked for an out-of-range point: " + pos);
		}
		return radiuses[pos];
	}

	/**
	 * Checks whether the nodes of this path have been assigned defined thickness.
	 *
	 * @return true, if the points defining with this path are associated with a
	 *         list of radii
	 */
	public boolean hasRadii() {
		return radiuses != null;
	}

	protected void setFittedCircles(final int nPoints, final double[] tangents_x, final double[] tangents_y, final double[] tangents_z,
			final double[] radiuses, final double[] optimized_x, final double[] optimized_y,
			final double[] optimized_z) {

		this.points = nPoints;
		this.tangents_x = tangents_x.clone();
		this.tangents_y = tangents_y.clone();
		this.tangents_z = tangents_z.clone();

		this.radiuses = radiuses.clone();

		this.precise_x_positions = optimized_x.clone();
		this.precise_y_positions = optimized_y.clone();
		this.precise_z_positions = optimized_z.clone();
	}

	public String realToString() {
		String name = getName();
		if (name == null)
			name = "Path " + id;
		if (size() == 1)
			name += " [Single Point]";
//		if (startJoins != null) {
//			name += ", starts on " + startJoins.getName();
//		}
//		if (endJoins != null) {
//			name += ", ends on " + endJoins.getName();
//		}
		if (swcType != SWC_UNDEFINED)
			name += " [" + getSWCtypeName(swcType, false) + "]";
		return name;
	}

	/**
	 * This toString() method shows details of the path which is actually being
	 * displayed, not necessarily this path object. FIXME: this is probably horribly
	 * confusing.
	 *
	 * @return the string
	 */

	@Override
	public String toString() {
		if (useFitted)
			return fitted.realToString();
		return realToString();
	}

	/**
	 * Sets the SWC type.
	 *
	 * @param newSWCType
	 *            the new SWC type
	 */
	public void setSWCType(final int newSWCType) {
		setSWCType(newSWCType, true);
	}

	protected void setSWCType(final int newSWCType, final boolean alsoSetInFittedVersion) {
		if (newSWCType < 0)
			throw new IllegalArgumentException("BUG: Unknown SWC type " + newSWCType);
		swcType = newSWCType;
		if (alsoSetInFittedVersion) {
			/*
			 * If we've been asked to also set the fitted version, this should only be
			 * called on the non-fitted version of the path, so raise an error if it's been
			 * called on the fitted version by mistake instead:
			 */
			if (isFittedVersionOfAnotherPath() && fittedVersionOf.getSWCType() != newSWCType)
				throw new IllegalArgumentException("BUG: only call setSWCType on the unfitted path");
			if (fitted != null)
				fitted.setSWCType(newSWCType);
		}
	}

	/**
	 * Gets the SWC type.
	 *
	 * @return the SWC type
	 */
	public int getSWCType() {
		return swcType;
	}

	/*
	 * @Override public String toString() { int n = size(); String result = ""; if(
	 * name != null ) result += "\"" + name + "\" "; result += n + " points"; if( n
	 * > 0 ) { result += " from " + x_positions[0] + ", " + y_positions[0] + ", " +
	 * z_positions[0]; result += " to " + x_positions[n-1] + ", " + y_positions[n-1]
	 * + ", " + z_positions[n-1]; } return result; }
	 */

	/**
	 * Gets the branching order of this Path
	 *
	 * @return the branching order (reverse Horton-Strahler order) of this path or A
	 *         primary path is always of order 1.
	 */
	public int getOrder() {
		return order;
	}

	protected void setOrder(final int order) {
		this.order = order;
		if (fitted != null)
			fitted.setOrder(order);
	}

	/*
	 * These are various fields that have the current 3D representations of this
	 * path. They should only be updated by synchronized methods, currently:
	 *
	 * updateContent3D addTo3DViewer removeFrom3DViewer
	 */
	int paths3DDisplay = 1;
	Content content3D;
	Content content3DExtra;
	ImagePlus content3DMultiColored;
	ImagePlus content3DExtraMultiColored;
	String nameWhenAddedToViewer;
	String nameWhenAddedToViewerExtra;

	synchronized void removeIncludingFittedFrom3DViewer(final Image3DUniverse univ) {
		removeFrom3DViewer(univ);
		if (useFitted)
			fitted.removeFrom3DViewer(univ);
	}

	synchronized void updateContent3D(final Image3DUniverse univ, final boolean visible, final int paths3DDisplay,
			final Color3f color, final ImagePlus colorImage) {

//		SNT.log("In updateContent3D, colorImage is: " + colorImage);
//		SNT.log("In updateContent3D, color is: " + color);

		// So, go through each of the reasons why we might
		// have to remove (and possibly add back) the path:

	
		if (!visible) {
			/*
			 * It shouldn't be visible - if any of the contents are non-null, remove them:
			 */
			removeIncludingFittedFrom3DViewer(univ);
			return;
		}

		// Now we know it should be visible.

		Path pathToUse = null;

		if (useFitted) {
			/*
			 * If the non-fitted versions are currently being displayed, remove them:
			 */
			removeFrom3DViewer(univ);
			pathToUse = fitted;
		} else {
			/*
			 * If the fitted version is currently being displayed, remove it:
			 */
			if (fitted != null) {
				fitted.removeFrom3DViewer(univ);
			}
			pathToUse = this;
		}

//		if (SNT.isDebugMode()) {
//			SNT.log("pathToUse is: " + pathToUse);
//			SNT.log("  pathToUse.content3D is: " + pathToUse.content3D);
//			SNT.log("  pathToUse.content3DExtra is: " + pathToUse.content3DExtra);
//			SNT.log("  pathToUse.content3DMultiColored: " + pathToUse.content3DMultiColored);
//		}

		// Is the the display (lines-and-discs or surfaces) right?
		if (pathToUse.paths3DDisplay != paths3DDisplay) {
			pathToUse.removeFrom3DViewer(univ);
			pathToUse.paths3DDisplay = paths3DDisplay;
			pathToUse.addTo3DViewer(univ, color, colorImage);
			return;
		}

		/* Were we previously using a colour image, but now not? */

		if (colorImage == null) {
			if ((paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_LINES_AND_DISCS
					&& pathToUse.content3DExtraMultiColored != null)
					|| (paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_SURFACE
							&& pathToUse.content3DMultiColored != null)) {
				pathToUse.removeFrom3DViewer(univ);
				pathToUse.addTo3DViewer(univ, color, colorImage);
				return;
			}

			/*
			 * ... or, should we now use a colour image, where previously we were using a
			 * different colour image or no colour image?
			 */

		} else {
			if ((paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_LINES_AND_DISCS
					&& pathToUse.content3DExtraMultiColored != colorImage)
					|| (paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_SURFACE
							&& pathToUse.content3DMultiColored != colorImage)) {
				pathToUse.removeFrom3DViewer(univ);
				pathToUse.addTo3DViewer(univ, color, colorImage);
				return;
			}
		}

		// Has the path's representation in the 3D viewer been marked as
		// invalid?

		if (pathToUse.is3DViewInvalid()) {
			pathToUse.removeFrom3DViewer(univ);
			pathToUse.addTo3DViewer(univ, color, colorImage);
			invalid3DMesh = false;
			return;
		}

		// Is the (flat) color wrong?

		if (pathToUse.realColor == null || !pathToUse.realColor.equals(color)) {

			/*
			 * If there's a representation of the path in the 3D viewer anyway, just set the
			 * color, don't recreate it, since the latter takes a long time:
			 */

			if (pathToUse.content3D != null || pathToUse.content3DExtra != null) {

				if (pathToUse.content3D != null)
					pathToUse.content3D.setColor(color);
				if (pathToUse.content3DExtra != null)
					pathToUse.content3DExtra.setColor(color);
				pathToUse.realColor = color;
				return;

			}
			// ... but if it wasn't in the 3D viewer, recreate it:
			pathToUse.removeFrom3DViewer(univ);
			pathToUse.paths3DDisplay = paths3DDisplay;
			pathToUse.addTo3DViewer(univ, color, colorImage);
			return;
		}

		if (pathToUse.nameWhenAddedToViewer == null || !univ.contains(pathToUse.nameWhenAddedToViewer)) {
			pathToUse.paths3DDisplay = paths3DDisplay;
			pathToUse.addTo3DViewer(univ, color, colorImage);
		}
	}

	synchronized public void removeFrom3DViewer(final Image3DUniverse univ) {
		if (content3D != null) {
			univ.removeContent(nameWhenAddedToViewer);
			content3D = null;
		}
		if (content3DExtra != null) {
			univ.removeContent(nameWhenAddedToViewerExtra);
			content3DExtra = null;
		}
	}

	protected List<PointInImage> getPointInImageList() {
		final ArrayList<PointInImage> linePoints = new ArrayList<>();
		for (int i = 0; i < points; ++i) {
			linePoints.add(new PointInImage(precise_x_positions[i], precise_y_positions[i], precise_z_positions[i]));
		}
		return linePoints;
	}

	public java.util.List<Point3f> getPoint3fList() {
		final ArrayList<Point3f> linePoints = new ArrayList<>();
		for (int i = 0; i < points; ++i) {
			linePoints.add(new Point3f((float) precise_x_positions[i], (float) precise_y_positions[i],
					(float) precise_z_positions[i]));
		}
		return linePoints;
	}

	protected boolean invalid3DMesh = false;


	private void invalidate3DView() {
		invalid3DMesh = true;
	}

	private boolean is3DViewInvalid() {
		return invalid3DMesh;
	}

	public Content addAsLinesTo3DViewer(final Image3DUniverse univ, final Color c, final ImagePlus colorImage) {
		return addAsLinesTo3DViewer(univ, new Color3f(c), colorImage);
	}

	public Content addAsLinesTo3DViewer(final Image3DUniverse univ, final Color3f c, final ImagePlus colorImage) {
		final String safeName = univ.getSafeContentName(getName() + " as lines");
		return univ.addLineMesh(getPoint3fList(), c, safeName, true);
	}

	public Content addDiscsTo3DViewer(final Image3DUniverse univ, final Color c, final ImagePlus colorImage) {
		return addDiscsTo3DViewer(univ, new Color3f(c), colorImage);
	}

	public Content addDiscsTo3DViewer(final Image3DUniverse univ, final Color3f c, final ImagePlus colorImage) {
		if (!hasRadii())
			return null;

		final Color3f[] originalColors = Pipe.getPointColors(precise_x_positions, precise_y_positions,
				precise_z_positions, c, colorImage);

		final List<Color3f> meshColors = new ArrayList<>();

		final int edges = 8;
		final List<Point3f> allTriangles = new ArrayList<>(edges * points);
		for (int i = 0; i < points; ++i) {
			final List<Point3f> discMesh = customnode.MeshMaker.createDisc(precise_x_positions[i],
					precise_y_positions[i], precise_z_positions[i], tangents_x[i], tangents_y[i], tangents_z[i],
					radiuses[i], 8);
			final int pointsInDiscMesh = discMesh.size();
			for (int j = 0; j < pointsInDiscMesh; ++j)
				meshColors.add(originalColors[i]);
			allTriangles.addAll(discMesh);
		}
		return univ.addTriangleMesh(allTriangles, meshColors, univ.getSafeContentName("Discs for path " + getName()));
	}

	synchronized public void addTo3DViewer(final Image3DUniverse univ, final Color c, final ImagePlus colorImage) {
		if (c == null)
			throw new IllegalArgumentException("In addTo3DViewer, Color can no longer be null");
		addTo3DViewer(univ, new Color3f(c), colorImage);
	}

	synchronized public void addTo3DViewer(final Image3DUniverse univ, final Color3f c, final ImagePlus colorImage) {
		if (c == null)
			throw new IllegalArgumentException("In addTo3DViewer, Color3f can no longer be null");

		realColor = (c == null) ? new Color3f(Color.magenta) : c;

		if (points <= 1) {
			content3D = null;
			content3DExtra = null;
			return;
		}

		if (paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_LINES
				|| paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_LINES_AND_DISCS) {
			content3D = addAsLinesTo3DViewer(univ, realColor, colorImage);
			content3D.setLocked(true);
			nameWhenAddedToViewer = content3D.getName();
			if (paths3DDisplay == SimpleNeuriteTracer.DISPLAY_PATHS_LINES_AND_DISCS) {
				content3DExtra = addDiscsTo3DViewer(univ, realColor, colorImage);
				content3DExtraMultiColored = colorImage;
				if (content3DExtra == null) {
					nameWhenAddedToViewerExtra = null;
				} else {
					content3DExtra.setLocked(true);
					nameWhenAddedToViewerExtra = content3DExtra.getName();
				}
			}
			// univ.resetView();
			return;
		}

		int pointsToUse = -1;

		double[] x_points_d = new double[points];
		double[] y_points_d = new double[points];
		double[] z_points_d = new double[points];
		double[] radiuses_d = new double[points];

		if (hasRadii()) {
			int added = 0;
			int lastIndexAdded = -noMoreThanOneEvery;
			for (int i = 0; i < points; ++i) {
				if ((points <= noMoreThanOneEvery) || (i - lastIndexAdded >= noMoreThanOneEvery)) {
					x_points_d[added] = precise_x_positions[i];
					y_points_d[added] = precise_y_positions[i];
					z_points_d[added] = precise_z_positions[i];
					radiuses_d[added] = radiuses[i];
					lastIndexAdded = i;
					++added;
				}
			}
			pointsToUse = added;
		} else {
			for (int i = 0; i < points; ++i) {
				x_points_d[i] = precise_x_positions[i];
				y_points_d[i] = precise_y_positions[i];
				z_points_d[i] = precise_z_positions[i];
				radiuses_d[i] = getMinimumSeparation() * 2;
			}
			pointsToUse = points;
		}

		if (pointsToUse == 2) {
			// If there are only two points, then makeTube
			// fails, so interpolate:
			final double[] x_points_d_new = new double[3];
			final double[] y_points_d_new = new double[3];
			final double[] z_points_d_new = new double[3];
			final double[] radiuses_d_new = new double[3];

			x_points_d_new[0] = x_points_d[0];
			y_points_d_new[0] = y_points_d[0];
			z_points_d_new[0] = z_points_d[0];
			radiuses_d_new[0] = radiuses_d[0];

			x_points_d_new[1] = (x_points_d[0] + x_points_d[1]) / 2;
			y_points_d_new[1] = (y_points_d[0] + y_points_d[1]) / 2;
			z_points_d_new[1] = (z_points_d[0] + z_points_d[1]) / 2;
			radiuses_d_new[1] = (radiuses_d[0] + radiuses_d[1]) / 2;

			x_points_d_new[2] = x_points_d[1];
			y_points_d_new[2] = y_points_d[1];
			z_points_d_new[2] = z_points_d[1];
			radiuses_d_new[2] = radiuses_d[1];

			x_points_d = x_points_d_new;
			y_points_d = y_points_d_new;
			z_points_d = z_points_d_new;
			radiuses_d = radiuses_d_new;

			pointsToUse = 3;
		}

		final double[] x_points_d_trimmed = new double[pointsToUse];
		final double[] y_points_d_trimmed = new double[pointsToUse];
		final double[] z_points_d_trimmed = new double[pointsToUse];
		final double[] radiuses_d_trimmed = new double[pointsToUse];

		System.arraycopy(x_points_d, 0, x_points_d_trimmed, 0, pointsToUse);
		System.arraycopy(y_points_d, 0, y_points_d_trimmed, 0, pointsToUse);
		System.arraycopy(z_points_d, 0, z_points_d_trimmed, 0, pointsToUse);
		System.arraycopy(radiuses_d, 0, radiuses_d_trimmed, 0, pointsToUse);

		/*
		 * Work out whether to resample or not. I've found that the resampling is only
		 * really required in cases where the points are at adjacent voxels. So, work
		 * out the mean distance between all the points but in image co-ordinates - if
		 * there are points only at adjacent voxels this will be between 1 and sqrt(3)
		 * ~= 1.73. However, after the "fitting" process here, we might remove many of
		 * these points, so I'll say that we won't resample if the mean is rather higher
		 * - above 3. Hopefully this is a good compromise...
		 */

		double total_length_in_image_space = 0;
		for (int i = 1; i < pointsToUse; ++i) {
			final double x_diff = (x_points_d_trimmed[i] - x_points_d_trimmed[i - 1]) / x_spacing;
			final double y_diff = (y_points_d_trimmed[i] - y_points_d_trimmed[i - 1]) / y_spacing;
			final double z_diff = (z_points_d_trimmed[i] - z_points_d_trimmed[i - 1]) / z_spacing;
			total_length_in_image_space += Math.sqrt(x_diff * x_diff + y_diff * y_diff + z_diff * z_diff);
		}
		final double mean_inter_point_distance_in_image_space = total_length_in_image_space / (pointsToUse - 1);
//		SNT.log("For path " + this + ", got mean_inter_point_distance_in_image_space: "
//				+ mean_inter_point_distance_in_image_space);
		final boolean resample = mean_inter_point_distance_in_image_space < 3;

//		SNT.log("... so" + (resample ? "" : " not") + " resampling");

		final ArrayList<Color3f> tubeColors = new ArrayList<>();

		final double[][][] allPoints = Pipe.makeTube(x_points_d_trimmed, y_points_d_trimmed, z_points_d_trimmed,
				radiuses_d_trimmed, resample ? 2 : 1, // resample - 1 means just
														// "use mean distance
														// between points", 3 is
														// three times that,
														// etc.
				12, // "parallels" (12 means cross-sections are dodecagons)
				resample, // do_resample
				realColor, colorImage, tubeColors);

		if (allPoints == null) {
			content3D = null;
			content3DExtra = null;
			return;
		}

		// Make tube adds an extra point at the beginning and end:

		final List<Color3f> vertexColorList = new ArrayList<>();
		final List<Point3f> triangles = Pipe.generateTriangles(allPoints, 1, // scale
				tubeColors, vertexColorList);

		nameWhenAddedToViewer = univ.getSafeContentName(getName());
		// univ.resetView();
		content3D = univ.addTriangleMesh(triangles, vertexColorList, nameWhenAddedToViewer);
		content3D.setLocked(true);
		content3DMultiColored = colorImage;

		content3DExtra = null;
		nameWhenAddedToViewerExtra = null;

		// univ.resetView();
		return;
	}

	public void setSelected(final boolean newSelectedStatus) {
		selected = newSelectedStatus;
	}

	public boolean isSelected() {
		return selected;
	}

	// TODO: this should be renamed
	public boolean versionInUse() {
		if (fittedVersionOf != null)
			return fittedVersionOf.useFitted;
		return !useFitted;
	}

	/**
	 * Returns an estimated volume of this path.
	 * <p>
	 * The most accurate volume of each path segment would be the volume of a convex
	 * hull of two arbitrarily oriented and sized circles in space. This is tough to
	 * work out analytically, and this precision isn't really warranted given the
	 * errors introduced in the fitting process, the tracing in the first place,
	 * etc. So, this method produces an approximate volume assuming that the volume
	 * of each of these parts is that of a truncated cone (Frustum) , with circles
	 * of the same size (i.e., as if the circles had simply been reoriented to be
	 * parallel and have a common normal vector)
	 * </p>
	 * <p>
	 * For more accurate measurements of the volumes of a neuron, you should use the
	 * filling interface.
	 * </p>
	 * 
	 * @return the approximate fitted volume (in physical units), or -1 if this Path
	 *         has no radii
	 * @see #hasRadii()
	 */
	public double getApproximatedVolume() {
		if (!hasRadii()) {
			return -1;
		}

		double totalVolume = 0;
		for (int i = 0; i < points - 1; ++i) {
			final double xdiff = precise_x_positions[i + 1] - precise_x_positions[i];
			final double ydiff = precise_y_positions[i + 1] - precise_y_positions[i];
			final double zdiff = precise_z_positions[i + 1] - precise_z_positions[i];
			final double h = Math.sqrt(xdiff * xdiff + ydiff * ydiff + zdiff * zdiff);
			final double r1 = radiuses[i];
			final double r2 = radiuses[i + 1];
			// See http://en.wikipedia.org/wiki/Frustum
			final double partVolume = (Math.PI * h * (r1 * r1 + r2 * r2 + r1 * r2)) / 3.0;
			totalVolume += partVolume;
		}

		return totalVolume;
	}

	/*
	 * This doesn't deal with the startJoins, endJoins or fitted fields, since they
	 * involve other paths which were probably also transformed by the caller.
	 */

	public Path transform(final PathTransformer transformation, final ImagePlus template, final ImagePlus model) {

		double templatePixelWidth = 1;
		double templatePixelHeight = 1;
		double templatePixelDepth = 1;
		String templateUnits = "pixels";

		final Calibration templateCalibration = template.getCalibration();
		if (templateCalibration != null) {
			templatePixelWidth = templateCalibration.pixelWidth;
			templatePixelHeight = templateCalibration.pixelHeight;
			templatePixelDepth = templateCalibration.pixelDepth;
			templateUnits = SNT.getSanitizedUnit(templateCalibration.getUnit());
		}

		final Path result = new Path(templatePixelWidth, templatePixelHeight, templatePixelDepth, templateUnits,
				size());
		final double[] transformed = new double[3];

		// Actually, just say you'll have to refit all the
		// previously fitted paths...

		for (int i = 0; i < points; ++i) {
			final double original_x = precise_x_positions[i];
			final double original_y = precise_y_positions[i];
			final double original_z = precise_z_positions[i];
			transformation.transformPoint(original_x, original_y, original_z, transformed);
			final double new_x = transformed[0];
			final double new_y = transformed[1];
			final double new_z = transformed[2];
			if (Double.isNaN(new_x) || Double.isNaN(new_y) || Double.isNaN(new_z))
				continue;
			result.addPointDouble(new_x, new_y, new_z);
		}

		result.id = id;
		result.selected = selected;
		result.name = name;

		result.x_spacing = x_spacing;
		result.y_spacing = y_spacing;
		result.z_spacing = z_spacing;
		result.spacing_units = spacing_units;

		result.swcType = swcType;

		return result;
	}

	/**
	 * Returns the points which are indicated to be a join, either in this Path
	 * object, or any other that starts or ends on it.
	 *
	 * @return the list of nodes as {@link PointInImage} objects
	 * @see #findJoinedPointIndices()
	 */
	public List<PointInImage> findJoinedPoints() {
		final ArrayList<PointInImage> result = new ArrayList<>();
		if (startJoins != null) {
			result.add(startJoinsPoint);
		}
		if (endJoins != null) {
			result.add(endJoinsPoint);
		}
		for (final Path other : somehowJoins) {
			if (other.startJoins == this) {
				result.add(other.startJoinsPoint);
			}
			if (other.endJoins == this) {
				result.add(other.endJoinsPoint);
			}
		}
		return result;
	}

	/**
	 * Returns the indices of points which are indicated to be a join, either in
	 * this path object, or any other that starts or ends on it.
	 *
	 * @return the indices of points (Path nodes)
	 * @see #findJoinedPoints()
	 */
	public Set<Integer> findJoinedPointIndices() {
		final HashSet<Integer> result = new HashSet<>();
		for (final PointInImage point : findJoinedPoints()) {
			result.add(indexNearestTo(point.x, point.y, point.z));
		}
		return result;
	}

	synchronized public void downsample(final double maximumAllowedDeviation) {
		// We should only downsample between the fixed points, i.e.
		// where this neuron joins others
		final Set<Integer> fixedPointSet = findJoinedPointIndices();
		// Add the start and end points:
		fixedPointSet.add(0);
		fixedPointSet.add(points - 1);
		final Integer[] fixedPoints = fixedPointSet.toArray(new Integer[0]);
		Arrays.sort(fixedPoints);
		int lastIndex = -1;
		int totalDroppedPoints = 0;
		for (final int fpi : fixedPoints) {
			if (lastIndex >= 0) {
				final int start = lastIndex - totalDroppedPoints;
				final int end = fpi - totalDroppedPoints;
				// Now downsample between those points:
				final ArrayList<SimplePoint> forDownsampling = new ArrayList<>();
				for (int i = start; i <= end; ++i) {
					forDownsampling.add(
							new SimplePoint(precise_x_positions[i], precise_y_positions[i], precise_z_positions[i], i));
				}
				final ArrayList<SimplePoint> downsampled = PathDownsampler.downsample(forDownsampling,
						maximumAllowedDeviation);

				// Now update x_points, y_points, z_points:
				final int pointsDroppedThisTime = forDownsampling.size() - downsampled.size();
				totalDroppedPoints += pointsDroppedThisTime;
				final int newLength = points - pointsDroppedThisTime;
				final double[] new_x_points = new double[maxPoints];
				final double[] new_y_points = new double[maxPoints];
				final double[] new_z_points = new double[maxPoints];
				// First copy the elements before 'start' verbatim:
				System.arraycopy(precise_x_positions, 0, new_x_points, 0, start);
				System.arraycopy(precise_y_positions, 0, new_y_points, 0, start);
				System.arraycopy(precise_z_positions, 0, new_z_points, 0, start);
				// Now copy in the downsampled points:
				final int downsampledLength = downsampled.size();
				for (int i = 0; i < downsampledLength; ++i) {
					final SimplePoint sp = downsampled.get(i);
					new_x_points[start + i] = sp.x;
					new_y_points[start + i] = sp.y;
					new_z_points[start + i] = sp.z;
				}
				System.arraycopy(precise_x_positions, end, new_x_points, (start + downsampledLength) - 1, points - end);
				System.arraycopy(precise_y_positions, end, new_y_points, (start + downsampledLength) - 1, points - end);
				System.arraycopy(precise_z_positions, end, new_z_points, (start + downsampledLength) - 1, points - end);

				double[] new_radiuses = null;
				if (hasRadii()) {
					new_radiuses = new double[maxPoints];
					System.arraycopy(radiuses, 0, new_radiuses, 0, start);
					for (int i = 0; i < downsampledLength; ++i) {
						final SimplePoint sp = downsampled.get(i);
						// Find a first and last index in the original radius
						// array to
						// take a mean over:
						int firstRadiusIndex, lastRadiusIndex, n = 0;
						double total = 0;
						if (i == 0) {
							// This is the first point:
							final SimplePoint spNext = downsampled.get(i + 1);
							firstRadiusIndex = sp.originalIndex;
							lastRadiusIndex = (sp.originalIndex + spNext.originalIndex) / 2;
						} else if (i == downsampledLength - 1) {
							// The this is the last point:
							final SimplePoint spPrevious = downsampled.get(i - 1);
							firstRadiusIndex = (spPrevious.originalIndex + sp.originalIndex) / 2;
							lastRadiusIndex = sp.originalIndex;
						} else {
							final SimplePoint spPrevious = downsampled.get(i - 1);
							final SimplePoint spNext = downsampled.get(i + 1);
							firstRadiusIndex = (sp.originalIndex + spPrevious.originalIndex) / 2;
							lastRadiusIndex = (sp.originalIndex + spNext.originalIndex) / 2;
						}
						for (int j = firstRadiusIndex; j <= lastRadiusIndex; ++j) {
							total += radiuses[j];
							++n;
						}
						new_radiuses[start + i] = total / n;
					}
					System.arraycopy(radiuses, end, new_radiuses, (start + downsampledLength) - 1, points - end);
				}

				// Now update all of those fields:
				points = newLength;
				precise_x_positions = new_x_points;
				precise_y_positions = new_y_points;
				precise_z_positions = new_z_points;
				radiuses = new_radiuses;
				if (hasRadii()) {
					setGuessedTangents(2);
				}
			}
			lastIndex = fpi;
		}
		invalidate3DView();
	}

	/**
	 * Assigns a fixed radius to all the nodes of this Path
	 *
	 * @param r
	 *            the radius to be assigned. Setting it to 0 or Double.NaN removes
	 *            the radius attribute from the Path
	 */
	public void setRadius(final double r) {
		if (Double.isNaN(r) || r == 0d) {
			radiuses = null;
		} else {
			if (radiuses == null) {
				createCircles();
				setGuessedTangents(2);
			}
			Arrays.fill(radiuses, r);
		}
	}

	/**
	 * Assigns radii to this Path
	 *
	 * @param radii
	 *            the radii array. Setting it null removes the radius attribute from
	 *            the Path
	 * @see #setRadius(double)
	 */
	public void setRadii(final double[] radii) {
		if (radii == null || radii.length == 0) {
			radiuses = null;
		} else if (radii != null && radii.length != size()) {
			throw new IllegalArgumentException("radii array must have as many elements as nodes");
		} else {
			if (radiuses == null) {
				createCircles();
				setGuessedTangents(2);
			}
			System.arraycopy(radii, 0, radiuses, 0, size());
		}
	}

}
