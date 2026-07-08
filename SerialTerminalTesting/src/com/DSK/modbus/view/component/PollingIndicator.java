package com.DSK.modbus.view.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.Timer;

public class PollingIndicator extends JPanel {
	private static final long serialVersionUID = 1L;
	private double angle = 0; // 회전 각도 (더 부드러운 계산을 위해 double 타입)
	private boolean isRunning = false;
	private Timer rotateTimer;

	// 💡 자연스러운 느낌을 위한 디자인 상수 정의
	private final Color MAIN_GREEN = new Color(40, 167, 69); // 오토폴링 메인 초록색
	private final float STROKE_WIDTH = 3.0f; // 약간 더 두껍게 해서 부드러움 강조
	private final int DIAMETER = 14; // 크기 미세 조정

	public PollingIndicator() {
		setPreferredSize(new Dimension(28, 28)); // 컴포넌트 크기 살짝 키움
		setOpaque(false); // 배경을 투명하게 해서 어디에나 어울리게 함

		// 💡 [개선 1] 타이머 주기를 16ms(초당 60프레임 수준)로 줄여 아주 부드럽게 움직이게 만듭니다.
		rotateTimer = new Timer(16, e -> {
			if (isRunning) {
				// 한 프레임당 8도씩 회전 (1000ms/16ms * 8도 = 초당 약 500도)
				angle = (angle + 8) % 360;
				repaint();
			}
		});
	}

	public void startAnimation() {
		if (!this.isRunning) {
			this.isRunning = true;
			this.rotateTimer.start();
		}
	}

	public void stopAnimation() {
		if (this.isRunning) {
			this.isRunning = false;
			this.rotateTimer.stop();
			this.angle = 0; // 정지 시 초기 각도로 리셋
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		// 💡 품질 설정 활성화 (매우 중요)
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		int w = getWidth();
		int h = getHeight();
		int x = (w - DIAMETER) / 2;
		int y = (h - DIAMETER) / 2;

		g2.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		if (isRunning) {
			// 💡 [개선 2] 그라데이션 꼬리 직접 구현
			// 360도를 잘게 쪼개서 각 부분마다 투명도를 다르게 주어 그립니다.

			int intAngle = (int) angle;

			// 꼬리의 길이를 정의합니다 (여기서는 270도 길이의 꼬리)
			int tailLength = 270;

			for (int i = 0; i < tailLength; i++) {
				// i가 클수록(즉, 현재 그리는 부분이 회전 방향에서 뒤쳐질수록) 투명해집니다.
				// 투명도(Alpha)를 255(완전 불투명)에서 0(완전 투명)까지 서서히 줄입니다.
				int alpha = (int) (255 * (1.0 - (double) i / tailLength));

				// 음수가 되거나 범위를 벗어나지 않게 제어 (0 이하로 떨어지면 그리지 않음)
				if (alpha < 5)
					break;

				// 메인 초록색에 투명도만 적용한 새로운 색상 생성
				Color trailColor = new Color(MAIN_GREEN.getRed(), MAIN_GREEN.getGreen(), MAIN_GREEN.getBlue(), alpha);
				g2.setColor(trailColor);

				// 현재 각도에서 뒤쪽으로 1도씩 잘게 쪼개진 호를 그립니다.
				// angle 값에 따라 시작 지점이 움직이므로 빙글빙글 도는 것처럼 보입니다.
				// angle 값을 음수 방향으로 빼주어야 회전 방향 뒤쪽으로 꼬리가 생깁니다.
				g2.drawArc(x, y, DIAMETER, DIAMETER, intAngle - i, 2);
			}
		} else {
			// 정지 상태일 때는 얌전한 회색 원으로 고정
			g2.setColor(new Color(200, 205, 210));
			g2.drawOval(x, y, DIAMETER, DIAMETER);
		}
	}
}