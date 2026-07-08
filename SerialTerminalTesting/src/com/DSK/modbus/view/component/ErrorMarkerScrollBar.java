package com.DSK.modbus.view.component;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.JScrollBar;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import com.DSK.modbus.model.ErrorMarkerDto;

public class ErrorMarkerScrollBar extends JScrollBar {

	private static final long serialVersionUID = 1L;

	/** 왼쪽 Marker 영역 폭 */
	private static final int MARKER_WIDTH = 12;

	private final JTextPane textPane;
	private final List<ErrorMarkerDto> markerList;

	public ErrorMarkerScrollBar(JTextPane textPane, List<ErrorMarkerDto> markerList) {

		super(JScrollBar.VERTICAL);

		this.textPane = textPane;
		this.markerList = markerList;

		// Marker 영역 확보
		setPreferredSize(new Dimension(20, 0));

		addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent e) {

				if (e.getX() <= MARKER_WIDTH && findMarker(e.getY()) != null) {

					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

				} else {

					setCursor(Cursor.getDefaultCursor());

				}
			}
		});

		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {

				// Marker 영역 클릭만 처리
				if (e.getX() > MARKER_WIDTH)
					return;

				ErrorMarkerDto marker = findMarker(e.getY());

				if (marker == null)
					return;

				SwingUtilities.invokeLater(() -> {

					try {

						Rectangle2D r = textPane.modelToView2D(marker.getOffset());

						if (r == null)
							return;

						textPane.setCaretPosition(marker.getOffset());

						Rectangle rect = r.getBounds();

						int visibleHeight = textPane.getVisibleRect().height;

						// 가운데 정도에 위치하도록
						rect.y -= visibleHeight / 2;

						if (rect.y < 0)
							rect.y = 0;

						textPane.scrollRectToVisible(rect);

						textPane.requestFocusInWindow();

					} catch (BadLocationException ex) {
						ex.printStackTrace();
					}

				});
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g) {

		super.paintComponent(g);

		int height = getHeight();

		synchronized (markerList) {

			int total = Math.max(textPane.getDocument().getLength(), 1);

			for (ErrorMarkerDto dto : markerList) {

				int y = dto.getOffset() * height / total;

				g.setColor(Color.RED);

				// Marker Strip만 그림
				g.fillRect(0, y, MARKER_WIDTH, 3);
			}
		}

		// Marker Strip 구분선
		g.setColor(new Color(220, 220, 220));
		g.drawLine(MARKER_WIDTH, 0, MARKER_WIDTH, getHeight());
	}

	private ErrorMarkerDto findMarker(int mouseY) {

		int height = getHeight();

		synchronized (markerList) {

			int total = Math.max(textPane.getDocument().getLength(), 1);

			for (ErrorMarkerDto dto : markerList) {

				int y = dto.getOffset() * height / total;

				if (Math.abs(mouseY - y) <= 5)
					return dto;
			}
		}

		return null;
	}

	public void refreshMarkers() {
		repaint();
	}
}