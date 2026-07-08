package com.DSK.modbus.view.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JProgressBar;
import javax.swing.Timer;

public class GradientProgressBar extends JProgressBar {
	private static final long serialVersionUID = 1L;

	private double wavePhase = 0;
	private boolean stripeAnimationEnabled = false;
	private Timer stripeTimer;

	public GradientProgressBar(int min, int max) {
		super(min, max);
		setOpaque(false);
		setBorderPainted(false);
		setStringPainted(false);

		stripeTimer = new Timer(25, e -> {
			if (stripeAnimationEnabled) {
				wavePhase += 0.15;
				if (wavePhase > Math.PI * 2) {
					wavePhase -= Math.PI * 2;
				}
				repaint();
			}
		});
	}

	public void startStripeAnimation() {
		stripeAnimationEnabled = true;
		if (!stripeTimer.isRunning()) {
			stripeTimer.start();
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		int width = getWidth();
		int height = getHeight();

		if (width <= 0 || height <= 0) {
			g2.dispose();
			return;
		}

		// 1. 배경 그리기 (바깥 테두리 선 안쪽으로 정확히 매칭되도록 1픽셀 축소)
		g2.setColor(new Color(242, 245, 248));
		g2.fillRoundRect(0, 0, width, height, height, height);

		double percent = getPercentComplete();

		if (percent > 0) {
			int progressWidth = (int) (width * percent);

			// 2. 진행바 그라데이션 베이스 그리기
			GradientPaint gp = new GradientPaint(0, 0, new Color(0, 75, 160), width, 0, new Color(108, 194, 74));
			g2.setPaint(gp);
			g2.fillRoundRect(0, 0, progressWidth, height, height, height);

			// 3. 🌊 자연스러운 물결(Wave) 애니메이션 효과 구현
			if (stripeAnimationEnabled) {
				// 💡 [개선 1] 단순히 사각형(0, 0, progressWidth, height)으로 자르면 
				// 왼쪽 곡면 모서리 밖으로 물결 색상이 삐져나가 테두리를 침범합니다.
				// 진행바 자체의 라운드 곡률에 맞춘 '둥근 사각형 클리핑 영역'을 지정하여 완벽하게 가둡니다.
				RoundRectangle2D clipRound = new RoundRectangle2D.Double(0, 0, progressWidth, height, height, height);
				g2.setClip(clipRound);

				g2.setColor(new Color(255, 255, 255, 45));

				Path2D.Double wavePath = new Path2D.Double();
				double waveBaseY = height * 0.25;
				double waveAmplitude = 3.5;
				double waveFrequency = 0.04;

				wavePath.moveTo(0, height);
				wavePath.lineTo(0, waveBaseY + Math.sin(wavePhase) * waveAmplitude);

				for (int x = 1; x <= progressWidth; x++) {
					double y = Math.sin(x * waveFrequency + wavePhase) * waveAmplitude + waveBaseY;
					wavePath.lineTo(x, y);
				}

				wavePath.lineTo(progressWidth, height);
				wavePath.closePath();

				g2.fill(wavePath);

				// 4. 입체감을 더하는 전체적인 은은한 광택 상단 하이라이트 효과
				GradientPaint gloss = new GradientPaint(0, 0, new Color(255, 255, 255, 60), 0, height / 2,
						new Color(255, 255, 255, 0));
				g2.setPaint(gloss);
				g2.fillRoundRect(0, 0, progressWidth, height, height, height);

				g2.setClip(null);
			}
		}

		// 💡 [개선 2] 변동 포인트 - 테두리 선 두께와 겹침 버그 완벽 수정
		// 기존 'width - 1' 방식은 드로잉 오프셋 때문에 선 하단과 우측이 내부 색상에 먹히거나 삐져나갔습니다.
		// 선 두께(1f)의 절반인 0.5픽셀만큼 안쪽으로 깎아서(Inset) 패스를 정밀하게 그려주면 
		// 채워진 색상 외곽을 부드러운 다크 실버 라인이 완벽하고 정갈하게 감싸 안아 겹침 현상이 사라집니다.
		float strokeWidth = 1.0f;
		g2.setStroke(new BasicStroke(strokeWidth));
		g2.setColor(new Color(205, 212, 220)); // 경계가 더 뚜렷하도록 테두리 색상 채도 미세 상향

		double offset = strokeWidth / 2.0;
		RoundRectangle2D borderRound = new RoundRectangle2D.Double(offset, offset, width - strokeWidth,
				height - strokeWidth, height, height);
		g2.draw(borderRound);

		g2.dispose();
	}
}