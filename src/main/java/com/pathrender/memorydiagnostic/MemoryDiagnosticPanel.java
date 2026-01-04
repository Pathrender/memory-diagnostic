package com.pathrender.memorydiagnostic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Singleton
public class MemoryDiagnosticPanel extends PluginPanel
{
	private final MemoryDiagnosticConfig config;
	private final ScrollablePanel listPanel = new ScrollablePanel();
	private final JLabel totalLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JLabel legendLabel = new JLabel();
	private final Insets rowInsets = new Insets(2, 6, 2, 6);
	private final Insets headerInsets = new Insets(0, 6, 8, 6);

	@Inject
	MemoryDiagnosticPanel(MemoryDiagnosticConfig config)
	{
		this.config = config;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(new EmptyBorder(10, 10, 10, 10));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel title = new JLabel("Plugin Memory (est.)");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		header.add(title, BorderLayout.WEST);

		totalLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(totalLabel, BorderLayout.EAST);

		add(header, BorderLayout.NORTH);

		listPanel.setLayout(new GridBagLayout());
		listPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setBorder(null);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scrollPane, BorderLayout.CENTER);

		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setText("Calculating...");

		legendLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		legendLabel.setHorizontalAlignment(SwingConstants.CENTER);
		legendLabel.setText("<html><div style='text-align:center'>Units = relative object+cache estimate<br>(not bytes)</div></html>");
		legendLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
		addFullRow(legendLabel, 0, headerInsets);
		addFullRow(statusLabel, 1, rowInsets);
	}

	void update(List<MemoryDiagnosticPlugin.PluginUsage> usage, long totalUnits)
	{
		List<MemoryDiagnosticPlugin.PluginUsage> snapshot = usage == null ? Collections.emptyList() : usage;
		SwingUtilities.invokeLater(() ->
		{
			listPanel.removeAll();
			int row = 0;
			addFullRow(legendLabel, row++, headerInsets);
			if (snapshot.isEmpty())
			{
				statusLabel.setText("Calculating...");
				addFullRow(statusLabel, row++, rowInsets);
			}
			else
			{
				int maxRows = Math.max(1, config.maxPlugins());
				int count = Math.min(maxRows, snapshot.size());
				for (int i = 0; i < count; i++)
				{
					MemoryDiagnosticPlugin.PluginUsage entry = snapshot.get(i);
					addRow(entry, totalUnits, row++);
				}

				if (snapshot.size() > count)
				{
					int remaining = snapshot.size() - count;
					JLabel more = new JLabel("+" + remaining + " more");
					more.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
					more.setHorizontalAlignment(SwingConstants.CENTER);
					more.setBorder(new EmptyBorder(6, 0, 0, 0));
					addFullRow(more, row++, rowInsets);
				}
			}

			addFillRow(row);
			totalLabel.setText(totalUnits > 0 ? formatUnits(totalUnits) : "");
			listPanel.revalidate();
			listPanel.repaint();
		});
	}

	void clear()
	{
		update(Collections.emptyList(), 0);
	}

	private void addRow(MemoryDiagnosticPlugin.PluginUsage entry, long totalUnits, int row)
	{
		TruncatingLabel name = new TruncatingLabel(entry.getName());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		name.setHorizontalAlignment(SwingConstants.CENTER);
		name.setToolTipText(entry.getName());
		Dimension namePref = name.getPreferredSize();
		name.setMinimumSize(new Dimension(0, namePref.height));

		long units = entry.getUnits();
		String percent = totalUnits > 0 ? formatPercent(units, totalUnits) : "0%";
		JLabel percentLabel = new JLabel(percent);
		percentLabel.setForeground(ColorScheme.BRAND_ORANGE);
		percentLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lockLabelWidth(percentLabel);

		JLabel unitsLabel = new JLabel(formatUnits(units));
		unitsLabel.setForeground(ColorScheme.BRAND_ORANGE);
		unitsLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lockLabelWidth(unitsLabel);

		addCell(name, row, 0, 1.0, GridBagConstraints.HORIZONTAL, rowInsets);
		addCell(percentLabel, row, 1, 0.0, GridBagConstraints.NONE, rowInsets);
		addCell(unitsLabel, row, 2, 0.0, GridBagConstraints.NONE, rowInsets);
	}

	private static String formatUnits(long units)
	{
		if (units >= 1_000_000)
		{
			return String.format(Locale.US, "%.1fM units", units / 1_000_000.0);
		}
		if (units >= 1_000)
		{
			return String.format(Locale.US, "%.1fk units", units / 1_000.0);
		}
		return units + " units";
	}

	private static String formatPercent(long units, long total)
	{
		double percent = (units * 100.0) / total;
		return String.format(Locale.US, "%.0f%%", percent);
	}

	private void addCell(JLabel label, int row, int col, double weightx, int fill, Insets insets)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = col;
		constraints.gridy = row;
		constraints.weightx = weightx;
		constraints.fill = fill;
		constraints.insets = insets;
		constraints.anchor = GridBagConstraints.CENTER;
		listPanel.add(label, constraints);
	}

	private void addFullRow(JLabel label, int row, Insets insets)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.gridwidth = 3;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = insets;
		constraints.anchor = GridBagConstraints.CENTER;
		listPanel.add(label, constraints);
	}

	private void addFillRow(int row)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.gridwidth = 3;
		constraints.weightx = 1.0;
		constraints.weighty = 1.0;
		constraints.fill = GridBagConstraints.VERTICAL;
		JPanel filler = new JPanel();
		filler.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listPanel.add(filler, constraints);
	}

	private static void lockLabelWidth(JLabel label)
	{
		Dimension preferred = label.getPreferredSize();
		label.setMinimumSize(preferred);
		label.setPreferredSize(preferred);
	}

	private static final class TruncatingLabel extends JLabel
	{
		private static final String ELLIPSIS = "...";
		private String fullText = "";

		private TruncatingLabel(String text)
		{
			setFullText(text);
		}

		void setFullText(String text)
		{
			fullText = text == null ? "" : text;
			updateDisplayedText();
		}

		@Override
		public void setText(String text)
		{
			setFullText(text);
		}

		@Override
		public void setBounds(int x, int y, int width, int height)
		{
			super.setBounds(x, y, width, height);
			updateDisplayedText();
		}

		@Override
		public void setFont(Font font)
		{
			super.setFont(font);
			updateDisplayedText();
		}

		private void updateDisplayedText()
		{
			if (fullText.isEmpty())
			{
				super.setText("");
				return;
			}

			int available = getWidth();
			if (available <= 0)
			{
				super.setText(fullText);
				return;
			}

			Insets insets = getInsets();
			available -= insets.left + insets.right;
			if (available <= 0)
			{
				super.setText(ELLIPSIS);
				return;
			}

			FontMetrics metrics = getFontMetrics(getFont());
			if (metrics.stringWidth(fullText) <= available)
			{
				super.setText(fullText);
				return;
			}

			int ellipsisWidth = metrics.stringWidth(ELLIPSIS);
			if (ellipsisWidth >= available)
			{
				super.setText(ELLIPSIS);
				return;
			}

			int low = 0;
			int high = fullText.length();
			while (low < high)
			{
				int mid = (low + high + 1) / 2;
				String candidate = fullText.substring(0, mid);
				if (metrics.stringWidth(candidate) + ellipsisWidth <= available)
				{
					low = mid;
				}
				else
				{
					high = mid - 1;
				}
			}

			String truncated = fullText.substring(0, low);
			super.setText(truncated + ELLIPSIS);
		}
	}

	private static final class ScrollablePanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(visibleRect.height - 16, 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
