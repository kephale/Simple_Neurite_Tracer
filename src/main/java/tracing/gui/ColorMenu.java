/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

package tracing.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import tracing.Path;

/**
 * This class generates a simplified color widget holding both predetermined
 * colors, user-defined ones and defaults for SWC types. It is based on Gerald
 * Bauer's code released under GPL2
 * (http://www.java2s.com/Code/Java/Swing-JFC/ColorMenu.htm)
 */
public class ColorMenu extends JMenu {

	private static final long serialVersionUID = 1L;
	private final Map<SWCColor, ColorPane> _colorPanes;
	private ColorPane _selectedColorPane;
	private final Border _activeBorder;
	private final Border _selectedBorder;
	private final Border _unselectedBorder;

	public ColorMenu(final String name) {
		super(name);

		_unselectedBorder = new CompoundBorder(new MatteBorder(2, 2, 2, 2,
			getBackground()), new MatteBorder(1, 1, 1, 1, getForeground()));

		_selectedBorder = new CompoundBorder(new MatteBorder(1, 1, 1, 1,
			getForeground().brighter()), new MatteBorder(2, 2, 2, 2,
				getForeground()));

		_activeBorder = new CompoundBorder(new MatteBorder(2, 2, 2, 2,
			getBackground().darker()), new MatteBorder(1, 1, 1, 1, getBackground()));

		final Color[] hues = new Color[] { Color.RED, Color.GREEN, Color.BLUE,
			Color.MAGENTA, Color.CYAN, Color.YELLOW, Color.ORANGE };
		final float[] colorRamp = new float[] { .75f, .5f, .3f };
		final float[] grayRamp = new float[] { 1, .86f, .71f, .57f, .43f, .29f, 0 };

		final JPanel defaultPanel = getGridPanel(8, 7);
		_colorPanes = new HashMap<>();

		final List<Color> colors = new ArrayList<>();
		for (final Color h : hues) {
			colors.add(h);
			final float[] hsbVals = Color.RGBtoHSB(h.getRed(), h.getGreen(), h
				.getBlue(), null);
			for (final float s : colorRamp) { // lighter colors
				final Color color = Color.getHSBColor(hsbVals[0], s * hsbVals[1],
					hsbVals[2]);
				colors.add(color);
			}
			for (final float s : colorRamp) { // darker colors
				final Color color = Color.getHSBColor(hsbVals[0], hsbVals[1], s *
					hsbVals[2]);
				colors.add(color);
			}
		}
		for (final float s : grayRamp) {
			final Color color = Color.getHSBColor(0, 0, s);
			colors.add(color);
		}
		for (final Color color : colors) {
			final ColorPane colorPane = new ColorPane(new SWCColor(color), false);
			defaultPanel.add(colorPane);
			_colorPanes.put(new SWCColor(color), colorPane);
		}
		add(defaultPanel);

		// Build the custom color row
		addSeparator("Custom... (Righ-click to change):");
		final JPanel customPanel = getGridPanel(1, 7);
		for (int i = 0; i < 7; i++) {
			final Color uniquePlaceHolderColor = new Color(getBackground().getRed(),
				getBackground().getGreen(), getBackground().getBlue(), 255 - i - 1);
			final ColorPane customColorPane = new ColorPane(new SWCColor(
				uniquePlaceHolderColor), true);
			customPanel.add(customColorPane);
			_colorPanes.put(new SWCColor(uniquePlaceHolderColor), customColorPane);
		}
		add(customPanel);

		// Build the panel for SWC colors
		addSeparator("SWC Type Colors (Righ-click to change):");
		final JPanel swcPanel = getGridPanel(1, 7);
		for (final int type : Path.getSWCtypes()) {
			final SWCColor swcColor = new SWCColor(Path.getSWCcolor(type), type);
			final ColorPane swcColorPane = new ColorPane(swcColor, true);
			swcPanel.add(swcColorPane);
			_colorPanes.put(swcColor, swcColorPane);
		}
		add(swcPanel);
		addSeparator();
	}

	private void addSeparator(final String title) {
		final JPanel panelLabel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
		panelLabel.setBackground(getBackground());
		final JLabel label = new JLabel(title);
		label.setFont(getFont().deriveFont((float) (getFont().getSize() / 1.5)));
		panelLabel.add(label);
		add(panelLabel);
	}

	private JPanel getGridPanel(final int rows, final int cols) {
		final JPanel panel = new JPanel();
		panel.setBackground(getBackground());
		panel.setBorder(BorderFactory.createEmptyBorder());
		panel.setLayout(new GridLayout(rows, cols));
		return panel;
	}

	public void selectColor(final Color c) {
		selectSWCColor(new SWCColor(c, SWCColor.SWC_TYPE_IGNORED));
	}

	public void selectSWCColor(final SWCColor c) {
		final Object obj = _colorPanes.get(c);
		if (obj == null) {
			selectNone();
			return;
		}
		if (_selectedColorPane != null) _selectedColorPane.setSelected(false);
		_selectedColorPane = (ColorPane) obj;
		_selectedColorPane.setSelected(true);
	}

	public SWCColor getSelectedSWCColor() {
		if (_selectedColorPane == null) return null;
		return _selectedColorPane.swcColor;
	}

	public void selectNone() {
		for (final Map.Entry<SWCColor, ColorPane> entry : _colorPanes.entrySet()) {
			entry.getValue().setSelected(false);
		}
	}

	public Color getCurrentColorForSWCType(final int swcType) {
		for (final Map.Entry<SWCColor, ColorPane> entry : _colorPanes.entrySet()) {
			if (entry.getKey().type() == swcType) return entry.getKey().color();
		}
		return null;
	}

	private void doSelection() {
		fireActionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
			getActionCommand()));
	}

	private class ColorPane extends JPanel implements MouseListener {

		private static final long serialVersionUID = 1L;
		private SWCColor swcColor;
		private boolean isSelectected;
		private final boolean isCustomizable;

		public ColorPane(final SWCColor sColor, final boolean customizable) {
			swcColor = sColor;
			isCustomizable = customizable;
			setPanelSWCColor(swcColor);
			setBorder(_unselectedBorder);
			addMouseListener(this);
		}

		private void setPanelSWCColor(final SWCColor sColor) {
			swcColor = sColor;
			setBackground(swcColor.color());
			final String msg = (swcColor.type() == SWCColor.SWC_TYPE_IGNORED)
				? "RGB: " + swcColor.color().getRed() + ", " + swcColor.color()
					.getGreen() + ", " + swcColor.color().getBlue() : Path.getSWCtypeName(
						swcColor.type());
			setToolTipText(msg);
		}

		public void setSelected(final boolean isSelected) {
			isSelectected = isSelected;
			if (isSelectected) {
				setBorder(_selectedBorder);
				_selectedColorPane = this;
			}
			else {
				setBorder(_unselectedBorder);
			}
		}

		@Override
		public Dimension getMaximumSize() {
			return getPreferredSize();
		}

		@Override
		public Dimension getMinimumSize() {
			return getPreferredSize();
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(15, 15);
		}

		@Override
		public void mouseClicked(final MouseEvent ev) {}

		@Override
		public void mouseEntered(final MouseEvent ev) {
			setBorder(_activeBorder);
		}

		@Override
		public void mouseExited(final MouseEvent ev) {
			setBorder(isSelectected ? _selectedBorder : _unselectedBorder);
		}

		@Override
		public void mousePressed(final MouseEvent ev) {

			// Ensure only this pane gets selected
			selectNone();
			setSelected(true);

			if (isCustomizable && (SwingUtilities.isRightMouseButton(ev) || ev
				.isPopupTrigger()))
			{

				// Remember menu path so that it can be restored after prompt
				final MenuElement[] path = MenuSelectionManager.defaultManager()
					.getSelectedPath();

				// Prompt user for new color
				final GuiUtils gUtils = new GuiUtils(getTopLevelAncestor());
				final String promptTitle = (swcColor
					.type() == SWCColor.SWC_TYPE_IGNORED) ? "New Color"
						: "New color for SWC Type: " + Path.getSWCtypeName(swcColor.type());
				final Color c = gUtils.getColor(promptTitle, swcColor.color(), "RGB");
				if (c != null && !c.equals(swcColor.color())) {
					// New color choice: refresh panel
					swcColor.setAWTColor(c);
					setBackground(c);
				}
				// Restore menu
				MenuSelectionManager.defaultManager().setSelectedPath(path);

			}
			else { // Dismiss menu
				MenuSelectionManager.defaultManager().clearSelectedPath();
			}

			doSelection();

		}

		@Override
		public void mouseReleased(final MouseEvent ev) {}
	}

	/** IDE debug method */
	public static void main(final String[] args) {
		final javax.swing.JFrame f = new javax.swing.JFrame();
		final javax.swing.JMenuBar menuBar = new javax.swing.JMenuBar();
		final ColorMenu menu = new ColorMenu("Test");
		menu.addActionListener(new java.awt.event.ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				System.out.println(e);
				System.out.println(menu.getSelectedSWCColor().color());
				System.out.println("Type: " + menu.getSelectedSWCColor().type());
				System.out.println(Path.getSWCtypeName(menu.getSelectedSWCColor()
					.type()));
			}
		});
		menuBar.add(menu);
		f.setJMenuBar(menuBar);
		f.pack();
		f.setVisible(true);
	}

}