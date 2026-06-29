import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

public class ReadCanvas extends GameCanvas {

	private static final int WIDTH_MARGIN = 2;
	public static final int STYLE_STRIKETHROUGH = 1;
	public static final int STYLE_SPOILER = 2;

	int layoutWidth;
	Vector parsed = new Vector();
	Vector render;
	Vector urls;

	int contentHeight;
	int width, height;

	public boolean scrolling;

	float kineticScroll;
	int scroll;
	int scrollTarget = -1;

	int lastX, lastY;
	int pressX, pressY;
	long pressTime;
	boolean pressed, draggedMuch;

	static final int moveSamples = 10;
	int[] moves = new int[moveSamples];
	long[] moveTimes = new long[moveSamples];
	int movesIdx;

	long lastPaintTime;
	int scrollTimer;

	private Graphics graphics;

	public ReadCanvas() {
		super(false);
	}

	public void paint_(Graphics g) {
		long now = System.currentTimeMillis();
		long deltaTime = lastPaintTime == 0 ? 0 : now - lastPaintTime;
		if (deltaTime > 500) deltaTime = 500;
		lastPaintTime = now;

		int w = width = getWidth();
		int h = height = getHeight();

		g.setColor(0);
		g.fillRect(0, 0, w, h);

		layout(w);

		boolean animate = false;

		if (kineticScroll != 0) {
			if ((kineticScroll > -1 && kineticScroll < 1) || contentHeight <= h || scroll <= 0 || scroll >= contentHeight - h) {
				kineticScroll = 0;
			} else {
				float mul = pressed && !draggedMuch ? 0.5f : 0.965f;
				scroll += (int) kineticScroll;
				kineticScroll *= mul;
				float f = deltaTime > 33 ? deltaTime / 33f : 1f;
				if (f >= 2) {
					int j = (int) f - 1;
					for (int i = 0; i < j; i++) {
						scroll += (int) kineticScroll;
						kineticScroll *= mul;
					}
				}
				scrollTimer = 0;
				animate = true;
			}
		}

		if (scrollTarget >= 0) {
			scrollTimer = 0;
			if (contentHeight <= h) {
				scroll = 0;
				scrollTarget = -1;
			} else {
				if (Math.abs(scroll - scrollTarget) < 1) {
					scroll = scrollTarget;
					scrollTarget = -1;
				} else {
					scroll = (int) lerp(scroll, scrollTarget, deltaTime > 33 ? 4 * deltaTime / 33f : 4F, 20F);
					animate = true;
				}
			}
		}

		int scroll = this.scroll;
		if (scroll < 0) this.scroll = scroll = 0;
		else if (contentHeight <= h) this.scroll = scroll = 0;
		else if (scroll > contentHeight - h) this.scroll = scroll = contentHeight - h;

		if (render != null) {
			g.setColor(0xFFFFFF);
			int l = render.size();
			for (int i = 0; i < l; ++i) {
				Object[] obj = (Object[]) render.elementAt(i);
				int[] pos = (int[]) obj[3];
				int[] styleColor = (int[]) obj[4];
				
				int ty = pos[1] - scroll;
				int th = pos[3];

				if (ty + th < 0) continue;
				if (ty > h) break;

				Font font = (Font) obj[1];
				String text = (String) obj[0];
				int tx = pos[0] + WIDTH_MARGIN;

				g.setFont(font);
				g.drawString(text, tx, ty, 0);

				if (styleColor != null && (styleColor[0] & STYLE_STRIKETHROUGH) != 0) {
					int ly = ty + (th >> 1) + 1;
					g.drawLine(tx, ly, tx + pos[2], ly);
				}
			}
		}
		
		// scroll bar
		if (scrollTimer < 1 && contentHeight > h) {
			scrollTimer++;
			g.setColor(0xABABAB);
			int sw = 4;
			int hh = contentHeight;
			if (hh <= 0) hh = 1;
			int sby = (int) (((float) scroll / (float) hh) * h);
			int sbh = (int) (((float) h / (float) hh) * h);
			g.fillRect(w - sw, sby, sw - 2, sbh);
		}

		this.scrolling = animate;
		if (animate) {
			Ran.repaint();
		}
	}

	public synchronized void layout(int w) {
		if (layoutWidth == w) return;
		layoutWidth = w;
		int widthLimit = w - WIDTH_MARGIN * 2;

		if (render == null) render = new Vector();
		else render.removeAllElements();
		if (urls == null) urls = new Vector();
		else urls.removeAllElements();

		Vector res = render;
		int x = 0, y = 0, idx = 0, mw = 0;
		int fh = 0;
		int l = parsed.size();
		int[] out = new int[4];

		for (int ei = 0; ei < l; ++ei) {
			int startIdx = idx;
			Object[] e = (Object[]) parsed.elementAt(ei);
			String text = (String) e[0];
			Font font = (Font) e[1];
			String url = (String) e[2];
			int[] styleColor = (int[]) e[3];

			fh = font.getHeight();
			if (text == null || "\n".equals(text)) {
				x = 0;
				y += fh;
				continue;
			}

			int ch = 0;
			int sl = text.length();
			char c;
			while (ch < sl && ((c = text.charAt(ch)) < ' ')) {
				ch++;
				if (c != '\n') continue;
				x = 0;
				y += fh;
			}

			if (text.indexOf('\n', ch) == -1) {
				split(text, font, url, widthLimit, x, y, idx, mw, ch, sl, fh, res, false, styleColor, out);
				x = out[0]; y = out[1]; idx = out[2]; mw = out[3];
			} else {
				int j = ch;
				for (int i = ch; i < sl; ++i) {
					if ((c = text.charAt(i)) == '\n') {
						split(text, font, url, widthLimit, x, y, idx, mw, j, i, fh, res, false, styleColor, out);
						x = 0; y = out[1] + fh; idx = out[2]; mw = out[3];
						j = i + 1;
					}
				}
				if (j != sl) {
					split(text, font, url, widthLimit, x, y, idx, mw, j, sl, fh, res, false, styleColor, out);
					x = out[0]; y = out[1]; idx = out[2]; mw = out[3];
				}
			}

			if (url != null) {
				Vector v = new Vector();
				for (int i = startIdx; i < idx; ++i) v.addElement(res.elementAt(i));
				urls.addElement(new Object[] { url, v });
			}
		}
		this.contentHeight = y + fh * 2;
	}

	public void paint() {
		paint_(graphics == null ? graphics = getGraphics() : graphics);
		flushGraphics();
	}

	protected void showNotify() {
		graphics = null;
		needRepaint();
	}

	protected void hideNotify() {
		graphics = null;
	}

	protected void sizeChanged(int w, int h) {
		graphics = null;
		needRepaint();
	}

	protected void keyPressed(int key) {
		key(key, false);
	}

	protected void keyRepeated(int key) {
		key(key, true);
	}

	private void key(int key, boolean repeat) {
		int game = getGameAction(key);
		if (game == Canvas.DOWN) {
			scrollTarget = scroll + height / 8;
			kineticScroll = 0;
			needRepaint();
		} else if (game == Canvas.UP) {
			scrollTarget = scroll - height / 8;
			kineticScroll = 0;
			needRepaint();
		} else if (key == -7) {
			Ran.midlet.commandAction(Ran.backCmd, this);
		}
	}

	public void pointerPressed(int x, int y) {
		kineticScroll = 0;
		pressed = true;
		draggedMuch = false;
		pressX = lastX = x;
		pressY = lastY = y;
		pressTime = System.currentTimeMillis();
		movesIdx = 0;
		for (int i = 0; i < moveSamples; i++) moveTimes[i] = 0;
		needRepaint();
	}

	public void pointerDragged(int x, int y) {
		long now = System.currentTimeMillis();
		final int dY = lastY - y;
		
		if (Math.abs(pressX - x) > 5 || Math.abs(pressY - y) > 5) {
			draggedMuch = true;
		}

		scroll += dY;
		if (kineticScroll * dY < 0) kineticScroll = 0;
		scrollTarget = -1;

		int prev = movesIdx - 1;
		if (prev < 0) prev += moveSamples;
		long prevTime = moveTimes[prev];
		if (now - prevTime <= 1) {
			moves[prev] += dY;
			moveTimes[prev] = now;
		} else {
			moves[movesIdx] = dY;
			moveTimes[movesIdx] = now;
			movesIdx = (movesIdx + 1) % moveSamples;
		}

		lastX = x;
		lastY = y;
		
		needRepaint();
	}

	public void pointerReleased(int x, int y) {
		long now = System.currentTimeMillis();
		if (!draggedMuch) {
//			if (now - pressTime < 300) {
//				tap(x, y);
//			}
		} else {
			int move = 0;
			long moveTime = 0, lastTime = 0;
			for (int i = 0; i < moveSamples; i++) {
				int idx = (movesIdx + moveSamples - 1 - i) % moveSamples;
				long time = moveTimes[idx];
				if (time == 0) break;
				if (i == 0) lastTime = time;
				if ((time = now - time) > 200) break;
				move += moves[idx];
				moveTime += time;
			}
			if (moveTime == 0) moveTime = 1;
			long holdTime = now - lastTime;
			if (moveTime > 0 && holdTime < 150) {
				float res = (130f * move) / moveTime;
				if (holdTime > 28) {
					if (Math.abs(res) > Math.abs(move)) res = move;
					res *= 25f / (holdTime - 10);
				}
				float abs = Math.abs(res);
				if (abs >= 1) {
					if (abs > 100) res = (res < 0 ? -60 : 60);
					if (kineticScroll * res < 0) kineticScroll = 0;
					kineticScroll += res;
				}
			}
		}
		pressed = false;
		needRepaint();
	}

//	private void tap(int x, int y) {
//		int idx = getUrlAt(x - WIDTH_MARGIN, y + scroll);
//		if (idx != -1) {
//			Object[] o = (Object[]) urls.elementAt(idx);
//			String url = (String) o[0];
//		}
//	}
//
//	private int getUrlAt(int x, int y) {
//		if (urls == null) return -1;
//		int l = urls.size();
//		for (int i = 0; i < l; ++i) {
//			Vector v = (Vector) ((Object[]) urls.elementAt(i))[1];
//			int l2 = v.size();
//			for (int j = 0; j < l2; ++j) {
//				Object[] o = (Object[]) v.elementAt(j);
//				int[] pos = (int[]) o[3];
//				if (x >= pos[0] && x < pos[0] + pos[2] && y >= pos[1] && y < pos[1] + pos[3]) {
//					return i;
//				}
//				if (pos[1] > y) break;
//			}
//		}
//		return -1;
//	}

	private void needRepaint() {
		Ran.repaint();
	}

	static float lerp(float start, float target, float mul, float div) {
		return start + ((target - start) * mul / div);
	}

	private static void split(String text, Font font, String url, int width, int x, int y, int idx, int mw, int ch, int sl, int fh, Vector res, boolean center, int[] styleColor, int[] out) {
		int dy = 0;
		if (res.size() != 0 && x != 0) {
			Font f = font;
			for (int i = res.size() - 1; i >= 0; --i) {
				int[] bounds = (int[]) ((Object[]) res.elementAt(i))[3];
				if (bounds[1] != y) break;
				f = (Font) ((Object[]) res.elementAt(i))[1];
			}
			dy = f.getBaselinePosition() - font.getBaselinePosition();
		}
		if (ch != sl) {
			int ew = font.substringWidth(text, ch, sl - ch);
			if (x + ew < width) {
				String t = text.substring(ch, sl);
				if (t.length() > 3 || t.trim().length() != 0) {
					res.addElement(new Object[] { t, font, url, new int[] { x, y + dy, ew, fh }, styleColor });
					idx++;
				}
				x += ew;
				mw = Math.max(mw, x);
			} else {
				for (int i = ch; i < sl; i++) {
					if (x + font.stringWidth(text.substring(ch, i + 1)) >= width) {
						w: {
							for (int j = i; j > ch; j--) {
								char c = text.charAt(j);
								if (c == ' ' || (c >= ',' && c <= '/')) {
									String t = text.substring(ch, ++j);
									int tw = font.stringWidth(t);
									if (center) x = centerRow(width, tw, x, y, res);
									if (t.length() > 3 || t.trim().length() != 0) {
										res.addElement(new Object[] { t, font, url, new int[] { x, y + dy, tw, fh }, styleColor });
										idx++;
									}
									mw = Math.max(mw, x + tw);
									x = 0; y += fh;
									i = ch = j;
									break w;
								}
							}
							String t = text.substring(ch, i);
							int tw = font.stringWidth(t);
							if (center) x = centerRow(width, tw, x, y, res);
							if (t.length() > 3 || t.trim().length() != 0) {
								res.addElement(new Object[] { t, font, url, new int[] { x, y + dy, tw, fh }, styleColor });
								idx++;
							}
							mw = Math.max(mw, x + tw);
							x = 0; y += fh;
							ch = i;
						}
					}
				}
				if (ch != sl) {
					String t = text.substring(ch, sl);
					int tw = font.stringWidth(t);
					if (t.length() > 3 || t.trim().length() != 0) {
						res.addElement(new Object[] { t, font, url, new int[] { x, y + dy, tw, fh }, styleColor });
						idx++;
					}
					x += tw;
					mw = Math.max(mw, x);
				}
			}
		}
		out[0] = x; out[1] = y; out[2] = idx; out[3] = mw;
	}

	private static int centerRow(int width, int t, int x, int y, Vector res) {
		int rw = (width - (x + t)) / 2;
		x += rw;
		for (int k = res.size() - 1; k >= 0; --k) {
			Object[] obj = (Object[]) res.elementAt(k);
			if (((int[]) obj[3])[1] == y) {
				((int[]) obj[3])[0] += rw;
			} else break;
		}
		return x;
	}

	public void beginParagraph() {
		append("\n", Ran.smallPlainFont);
	}

	public void endParagraph() {
		append("\n", Ran.smallPlainFont);
	}

	public void append(String text, Font font) {
		parsed.addElement(new Object[] { text, font, null, null });
		layoutWidth = 0;
		if (isShown()) needRepaint();
	}

}
