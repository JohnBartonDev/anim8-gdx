package com.github.tommyettinger.anim8;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;

import space.earlygrey.shapedrawer.ShapeDrawer;

public class Anim8Gui extends InputAdapter {
	
	private enum Anim8State {
		NORMAL, 
		SCALE,
		MOVE
	}
	private Anim8State state = Anim8State.NORMAL;
	
	public static final FileHandle DEFAULT_OUTPUT_DIRECTORY = Gdx.files.local("anim8Captures/");
	private FileHandle outputDirectory;
	
	private boolean isActive = true;
	private boolean isWriting;
	private boolean isCapturing;
	private boolean hideGui;
	private boolean captureScreenshot;
	private boolean usePNG8;
	
	private int activateKey = Keys.GRAVE;
	private final int fullScreenKey = Keys.F;
	private final int scaleAllKey = Keys.SHIFT_LEFT;
	private final int scaleAllAlternateKey = Keys.SHIFT_RIGHT;  
	private final int moveAllKey = Keys.SPACE;
	private final int resetKey = Keys.R;
	private final int startStopKey = Keys.S;
	
	private final int touchSize = 8;
	private final int boundarySize = 4;
	private boolean isLeftSelected;
	private boolean isRightSelected;
	private boolean isTopSelected;
	private boolean isBottomSelected;
	private final int rightOffset = boundarySize;
	private Vector2 touchPos;
	private Vector2 p1 = new Vector2();
	private Vector2 p2 = new Vector2();
	private Vector2 p3 = new Vector2();
	private Vector2 p4 = new Vector2();
	private Vector2 p1Tmp = new Vector2();
	private Vector2 p2Tmp = new Vector2();
	private Vector2 p3Tmp = new Vector2();
	private Vector2 p4Tmp = new Vector2();
	
	private final Array<Pixmap> frames;
	private BiConsumer<AnimatedGif, Array<Pixmap>> customAnimatedGifWriter;
	private BiConsumer<AnimatedPNG, Array<Pixmap>> customAnimatedPNGWriter;
	private BiConsumer<PNG8, Pixmap> customPNG8Writer;
	private AnimationWriter currentWriter;
	
	private Matrix4 oldProj;
	private Batch batch;
	private ShapeDrawer shapeDrawer;
	
	private boolean writeAnimatedGif;
	private boolean writeAnimatedPNG;
	private AnimatedGif animatedGif;
	private AnimatedPNG animatedPNG;
	private PNG8 png8;
	
	private Texture pixelTexture;
	
	private int frameWidth;
	private int frameHeight;
	private int screenWidth;
	private int screenHeight;
	
	private int frameLimit = -1;
	private int fps = 16;
	
	private DateTimeFormatter dateFormat;
	private DateTimeFormatter folderFormat;
	
	private boolean hideText;
	private float fontX;
	private float fontY;
	private float textBackgroundWidth = 150;
	private float textBackgroundHeight = 20;
	private BitmapFont font;
	private GlyphLayout writerTextLayout;
	private GlyphLayout fpsTextLayout;
	
	private float timer;
	private final float duration = 3f;
	private boolean showFramePlusFps;

	private static Texture createPixelTexture() {
		Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixmap.drawPixel(0, 0, 0xFFFFFFFF);
		Texture t = new Texture(pixmap);
		pixmap.dispose();
		return t;
	}
	
	public Anim8Gui(Batch batch) {
		this(batch, new TextureRegion(createPixelTexture()), DEFAULT_OUTPUT_DIRECTORY);
	}
	
	public Anim8Gui(Batch batch, FileHandle outputDirectory) {
		this(batch, new TextureRegion(createPixelTexture()), outputDirectory);
		pixelTexture = shapeDrawer.getRegion().getTexture();
	}
	
	public Anim8Gui(Batch batch, TextureRegion pixelRegion, FileHandle outputDirectory) {
		this(batch, new ShapeDrawer(batch, pixelRegion), outputDirectory);
	}
	
	public Anim8Gui(Batch batch, ShapeDrawer shapeDrawer, FileHandle outputDirectory) {
		this.batch = batch;
		this.shapeDrawer = shapeDrawer;
		this.outputDirectory = outputDirectory;
		
		font = new BitmapFont();
		font.setColor(Color.BLACK);
		writerTextLayout = new GlyphLayout();
		
		setText("Default");
		
		if(outputDirectory == null) outputDirectory = DEFAULT_OUTPUT_DIRECTORY;
		
		dateFormat = DateTimeFormatter.ofPattern("dd-hh_mm_s_a");
		folderFormat = DateTimeFormatter.ofPattern("yyyy-MM");
		
		final int width = 50;
		final int height = 50;
		final int centerX = Gdx.graphics.getWidth() / 2;
		final int centerY = Gdx.graphics.getHeight() / 2;
		
		p1 = new Vector2(centerX - width / 2, centerY - height / 2);
		p2 = new Vector2(p1.x, p1.y + height);
		p3 = new Vector2(p1.x + width, p1.y + height);
		p4 = new Vector2(p1.x + width, p1.y);
		
		touchPos = new Vector2();
		
		oldProj = new Matrix4();
		frames = new Array<>(60);
		
		png8 = new PNG8();
		png8.setCompression(7);
	}
	
	public void screenShot() {
		if(isWriting || captureScreenshot) return;
		
		int frameWidth = (int)(p4.x - p1.x);
		int frameHeight = (int)(p2.y - p1.y);
		
		Pixmap screenshotFrame = ScreenUtils.getFrameBufferPixmap((int)p1.x, (int)p1.y, frameWidth, frameHeight);
		
		//Current time
		final LocalDateTime time = LocalDateTime.now();

		//Get the folder for the current year and month to store the captures
		final FileHandle folderHandle = Gdx.files.local(outputDirectory.path() + '/' + folderFormat.format(time));
		if(!folderHandle.isDirectory()) folderHandle.mkdirs();

		final String fileName = dateFormat.format(time);
		
		if(usePNG8) {
			if(customPNG8Writer != null) {
				customPNG8Writer.accept(png8, screenshotFrame);
			}
			else {
				png8.write(Gdx.files.local(folderHandle.path() + '/' + fileName + ".png"), screenshotFrame);
			}
		}
		else {
			PixmapIO.writePNG(Gdx.files.local(folderHandle.path() + '/' + fileName + "-old" + ".png"), screenshotFrame, 5, true);
		}
	}
	
	public void start() {
		if(isWriting || isCapturing) return;
		isCapturing = true;
		hideGui = true;
		frameWidth = (int)(p4.x - p1.x);
		frameHeight = (int)(p2.y - p1.y);
	}
	
	public void stop() {
		if(isWriting || !isCapturing) return;
		isCapturing = false;
		hideGui = false;
		writeToFile();
	}
	
	public void usePNG8(boolean usePNG8) {
		this.usePNG8 = usePNG8;
	}
	
	public void setCustomAnimatedGifWriter(BiConsumer<AnimatedGif, Array<Pixmap>> consumer) {
		customAnimatedGifWriter = consumer;
	}
	
	public void setCustomAnimatedPNGWriter(BiConsumer<AnimatedPNG, Array<Pixmap>> consumer) {
		customAnimatedPNGWriter = consumer;
	}
	
	public void setCustomPNG8Writer(BiConsumer<PNG8, Pixmap> consumer) {
		customPNG8Writer = consumer;
	}
	
	public void writeAnimatedGif(boolean write) {
		writeAnimatedGif(null, write);
	}
	
	public void writeAnimatedGif(AnimatedGif gif, boolean write) {
		if(gif == null) animatedGif = new AnimatedGif();
		writeAnimatedGif = write;
	}
	
	public void writeAnimatedPNG(boolean write) {
		writeAnimatedPNG(null, write);
	}
	
	public void writeAnimatedPNG(AnimatedPNG png, boolean write) {
		if(png == null) animatedPNG = new AnimatedPNG();
		writeAnimatedPNG = write;
	}
	
	public void setFrameLimit(int frameLimit) {
		this.frameLimit = frameLimit;
	}
	
	public void setFps(int fps) {
		this.fps = fps;
	}
	
	private void recordSec60fps() {
		frameLimit = 60;
		fps = 60;
	}
	
	private void recordSec30fps() {
		frameLimit = 30;
		fps = 30;
		showFramePlusFps = true;
		timer = 0;
	}
	
	public void dispose() {
		if(pixelTexture != null) pixelTexture.dispose();
	}
	
	private void hideText(boolean hide) {
		if(hideText == hide) return;
		
		if(!hideText && hide) {
			hideText = hide;
			return;
		}
		
		if(isWriting || !state.equals(Anim8State.NORMAL)) return;
		hideText = hide;
	}
	
	private void writeToFile() {
		if(frames.size == 0) return;
		isWriting = true;
		
		if(frameLimit == -1) {
//			fps = 
		}
		
		//Current time
		final LocalDateTime time = LocalDateTime.now();

		//Get the folder for the current year and month to store the captures
		final FileHandle folderHandle = Gdx.files.local(outputDirectory.path() + '/' + folderFormat.format(time));
		if(!folderHandle.isDirectory()) folderHandle.mkdirs();
		
		final String fileName = dateFormat.format(time);
		final String fullPath = folderHandle.path() + '/' + fileName;

		if(currentWriter == null) {
			writeAnimatedGif(fullPath);
			writeAnimatedPNG(fullPath);
		}
		else if(currentWriter == animatedGif) {
			writeAnimatedGif(fullPath);
		}
		else {
			writeAnimatedPNG(fullPath);
		}

		clear();
	}
	
	private void writeAnimatedGif(String path) {
		if(writeAnimatedGif) {
			if(customAnimatedGifWriter != null) {
				customAnimatedGifWriter.accept(animatedGif, frames);
			}
			else {
				animatedGif.write(Gdx.files.local(path + ".gif"), frames, fps);
			}
		}
	}
	
	private void writeAnimatedPNG(String path) {
		if(writeAnimatedPNG) {
			if(customAnimatedPNGWriter != null) {
				customAnimatedPNGWriter.accept(animatedPNG, frames);
			}
			else {
				animatedPNG.write(Gdx.files.local(path + ".apng"), frames, fps);
			}
		}
	}
	
	private void setText(String str) {
		writerTextLayout.setText(font, str);
		fontX = (textBackgroundWidth - writerTextLayout.width) / 2;
		fontY = (textBackgroundHeight - (writerTextLayout.height)) / 2;
	}
	
	private void captureFrame() {
		if(frameLimit > 0) {
			if(frames.size == frameLimit) {
				stop();
				return;
			}
		}
		frames.add(ScreenUtils.getFrameBufferPixmap((int)p1.x, (int)p1.y, frameWidth, frameHeight));
	}

	private void clear() {
		hideText = false;
		isWriting = false;
		frames.clear();
		hideGui = false;
		state = Anim8State.NORMAL;
	}
	
	private void saveTemp() {
		p1Tmp.set(p1);
		p2Tmp.set(p2);
		p3Tmp.set(p3);
		p4Tmp.set(p4);
	}
	
	private void setSelected(boolean selected) {
		hideText(false);
		isLeftSelected = selected;
		isRightSelected = selected;
		isTopSelected = selected;
		isBottomSelected = selected;
	}
	
	private void updateTouchPos(int screenX, int screenY) {
		touchPos.set(screenX, Gdx.graphics.getHeight() - screenY);
	}

	private boolean isSelected(float x, float y, float width, float height, float touchX, float touchY) {
		return touchX >= x && touchX <= (x + width) && touchY >= y && touchY <= (y + height);
	}
	
	@Override
	public boolean keyDown(int keycode) {
		if(keycode == activateKey) {
			isActive = !isActive;
		}
		
		if(!isActive || isWriting) return false;
		
		switch(keycode) {
			case fullScreenKey:
				p1.set(0, 0);
				p2.set(0, Gdx.graphics.getHeight());
				p3.set(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
				p4.set(Gdx.graphics.getWidth(), 0);
				return true;
			case scaleAllKey:
			case scaleAllAlternateKey:
				hideText(true);
				state = Anim8State.SCALE;
				setSelected(true);
				saveTemp();
				updateTouchPos(Gdx.input.getX(), Gdx.input.getY());
				return true;
			case moveAllKey:
				hideText(true);
				state = Anim8State.MOVE;
				setSelected(true);
				saveTemp();
				updateTouchPos(Gdx.input.getX(), Gdx.input.getY());
				return true;
			case startStopKey:
				if(Gdx.input.isKeyPressed(Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Keys.CONTROL_RIGHT)) {
					screenShot();
				}
				else{
					if(!isCapturing) {
						start();
					}
					else {
						stop();
					}
				}
				return true;
			case resetKey:{
				if(screenWidth < 50 || screenHeight < 50) return false;
				final int width = 50;
				final int height = 50;
				final int centerX = Gdx.graphics.getWidth() / 2;
				final int centerY = Gdx.graphics.getHeight() / 2;
				
				p1.set(centerX - width / 2, centerY - height / 2);
				p2.set(p1.x, p1.y + height);
				p3.set(p1.x + width, p1.y + height);
				p4.set(p1.x + width, p1.y);
				return true;
			}
			case Keys.NUM_1:
				if(animatedGif == null) return false;
				setText("Gif");
				currentWriter = animatedGif;
				return true;
			case Keys.NUM_2:
				if(animatedPNG == null) return false;
				setText("Animated PNG");
				currentWriter = animatedPNG;
				return true;
			case Keys.NUM_3:
				setText("Default");
				currentWriter = null;
				return true;
			case Keys.NUM_4:
				recordSec30fps();
				return true;
			default:
				return false;
		}
	}
	
	@Override
	public boolean keyUp(int keycode) {
		if(!isActive || isWriting) return false;
		
		switch(keycode) {
			case scaleAllKey:
			case scaleAllAlternateKey:
				if(state.equals(Anim8State.SCALE)) {
					setSelected(false);
					state = Anim8State.NORMAL;
					hideText(false);
				}
				return true;
			case moveAllKey:
				if(state.equals(Anim8State.MOVE)) {
					setSelected(false);
					state = Anim8State.NORMAL;
					hideText(false);
				}
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		if(!isActive || isWriting) return false;
		
		updateTouchPos(screenX, screenY);
		saveTemp();
		
		if(state.equals(Anim8State.NORMAL)) {
			final float width = p4.x - p1.x;
			final float height = p2.y - p1.y;
			
			isLeftSelected = isSelected(p1.x - (touchSize / 2), p1.y, boundarySize + touchSize, height, touchPos.x, touchPos.y);
			isRightSelected = isSelected(p4.x - rightOffset - (touchSize / 2), p4.y, boundarySize + touchSize, height, touchPos.x, touchPos.y);
			isTopSelected = isSelected(p2.x, p2.y - boundarySize - (touchSize / 2), width, boundarySize + touchSize, touchPos.x, touchPos.y);
			isBottomSelected = isSelected(p1.x, p1.y - (touchSize / 2), width, boundarySize + touchSize, touchPos.x, touchPos.y);
			
			if(isLeftSelected || isRightSelected || isTopSelected || isBottomSelected) hideText(true);
		}
		return false;
	}
	
	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		if(!isActive || isWriting) return false;
		if(state.equals(Anim8State.NORMAL)) setSelected(false);
		return false;
	}
	
	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		if(!isActive || isWriting) return false;
		
		//Flip y
		screenY = Gdx.graphics.getHeight() - screenY;
		
		switch(state) {
			case NORMAL:
				if(isLeftSelected) {
					p1.x = p2.x = MathUtils.clamp(screenX, 0, p3.x - (boundarySize + touchSize) - boundarySize);
				}
				
				if(isTopSelected) {
					p2.y = p3.y = MathUtils.clamp(screenY, p1.y + (boundarySize + touchSize) + boundarySize, Gdx.graphics.getHeight());
				}
				
				if(isBottomSelected) {
					p1.y = p4.y = MathUtils.clamp(screenY, 0, p2.y - (boundarySize + touchSize) - boundarySize);
				}
				
				if(isRightSelected) {
					p3.x = p4.x = MathUtils.clamp(screenX, p1.x + (boundarySize + touchSize) + boundarySize, Gdx.graphics.getWidth());
				}
				break;
			case SCALE: {
				final float xDst = Math.abs(touchPos.x - screenX);
				final float yDst = Math.abs(touchPos.y - screenY);
				float amount = (xDst + yDst) / 2f;
				
				if(screenY > touchPos.y) amount *= -1f;
	
				//Bottom left point
				final float p1X = p1Tmp.x - amount;
				final float p1Y = p1Tmp.y - amount;
				
				//Top left point
				final float p2X = p2Tmp.x - amount;
				final float p2Y = p2Tmp.y + amount;
				
				//Top right point
				final float p3X = p3Tmp.x + amount;
				final float p3Y = p3Tmp.y + amount;
				
				//Bottom right point
				final float p4X = p4Tmp.x + amount;
				final float p4Y = p4Tmp.y - amount;
				
				final float maxWidth = Gdx.graphics.getWidth();
				final float maxHeight = Gdx.graphics.getHeight();
				
				if(p1X < 0 || p1Y < 0 || p3X > maxWidth || p3Y > maxHeight) return false;
				if((p4X - p1X) <= (touchSize + boundarySize) || (p2Y - p1Y) <= (touchSize + boundarySize)) return false;
				
				p1.set(p1X, p1Y);
				p2.set(p2X, p2Y);
				p3.set(p3X, p3Y);
				p4.set(p4X, p4Y);
				break;
			}
			case MOVE: {
				final float xAmount = screenX - touchPos.x;
				final float yAmount = screenY - touchPos.y;
				
				float p1X = p1Tmp.x + xAmount;
				float p1Y = p1Tmp.y + yAmount;
				float p2X = p2Tmp.x + xAmount;
				float p2Y = p2Tmp.y + yAmount;
				float p3X = p3Tmp.x + xAmount;
				float p3Y = p3Tmp.y + yAmount;
				float p4X = p4Tmp.x + xAmount;
				float p4Y = p4Tmp.y + yAmount;
				
				//Make sure the record area doesn't go off screen
				if(p1X < 0) {
					final float dst = p4X - p1X;
					p1X = p2X = 0;
					p3X = p4X = dst;
				}
				else if(p3X > Gdx.graphics.getWidth()) {
					final float dst = p4X - p1X;
					p3X = p4X = Gdx.graphics.getWidth();
					p1X = p2X = p3X - dst; 
				}
				
				if(p1Y < 0) {
					final float dst = p2Y - p1Y;
					p1Y = p4Y = 0;
					p2Y = p3Y = dst;
				}
				else if(p2Y > Gdx.graphics.getHeight()) {
					final float dst = p2Y - p1Y;
					p2Y = p3Y = Gdx.graphics.getHeight();
					p1Y = p4Y = p2Y - dst;
				}
				
				p1.set(p1X, p1Y);
				p2.set(p2X, p2Y);
				p3.set(p3X, p3Y);
				p4.set(p4X, p4Y);
				break;
			}
		}
		return super.touchDragged(screenX, screenY, pointer);
	}
	
	private void checkForScreenChange() {
		if(screenWidth != Gdx.graphics.getWidth() || screenHeight != Gdx.graphics.getHeight()) {
			screenWidth = Gdx.graphics.getWidth();
			screenHeight = Gdx.graphics.getHeight();
			
			if(p4.x > screenWidth) {
				p4.x = p3.x = screenWidth;
			}
			
			if(p2.y > screenHeight) {
				p2.y = p3.y = screenHeight;
			}
		}
	}

	public void draw() {
		if(!isActive) return;
		
		checkForScreenChange();

		if(!hideGui) {
			boolean wasDrawing = batch.isDrawing();
			
			//Flush whatever the user was currently drawing
			if(wasDrawing) batch.end();
			
			ShaderProgram oldShader = batch.getShader();
			
			batch.setShader(null);
			oldProj.set(batch.getProjectionMatrix());
			batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			batch.begin();
			
			final float width = p4.x - p1.x;
			final float height = p2.y - p1.y;

			//Draw boundaries lines
			if(isLeftSelected) {
				shapeDrawer.filledRectangle(p1.x, p1.y, boundarySize, height, Color.RED);
			}
			else {
				shapeDrawer.filledRectangle(p1.x, p1.y, boundarySize, height, Color.BLACK);
			}
			
			if(isTopSelected) {
				shapeDrawer.filledRectangle(p2.x, p2.y - boundarySize, width, boundarySize, Color.RED);
			}
			else {
				shapeDrawer.filledRectangle(p2.x, p2.y - boundarySize, width, boundarySize, Color.BLACK);
			}
			
			if(isRightSelected) {
				shapeDrawer.filledRectangle(p4.x - boundarySize, p4.y, boundarySize, height, Color.RED);
			}
			else {
				shapeDrawer.filledRectangle(p4.x - boundarySize, p4.y, boundarySize, height, Color.BLACK);
			}
			
			if(isBottomSelected) {
				shapeDrawer.filledRectangle(p1.x, p1.y, width, boundarySize, Color.RED);
			}
			else {
				shapeDrawer.filledRectangle(p1.x, p1.y, width, boundarySize, Color.BLACK);
			}
			
			//Draw boundary points
			shapeDrawer.filledRectangle(p1.x, p1.y, boundarySize, boundarySize, Color.BLACK);
			shapeDrawer.filledRectangle(p2.x, p2.y - boundarySize, boundarySize, boundarySize, Color.BLACK);
			shapeDrawer.filledRectangle(p3.x - boundarySize, p3.y - boundarySize, boundarySize, boundarySize, Color.BLACK);
			shapeDrawer.filledRectangle(p4.x - boundarySize, p4.y, boundarySize, boundarySize, Color.BLACK);
			
			//Left
			debugTouchBounds(p1.x - (touchSize / 2), p1.y, boundarySize + touchSize, height);
			
			//RightT
			debugTouchBounds(p4.x - rightOffset - (touchSize / 2), p4.y, boundarySize + touchSize, height);
			
			//Top
			debugTouchBounds(p2.x, p2.y - boundarySize - (touchSize / 2), width, boundarySize + touchSize);
			
			//Bottom
			debugTouchBounds(p1.x, p1.y - (touchSize / 2), width, boundarySize + touchSize);
			
			if(!hideText) {
				shapeDrawer.filledRectangle(0, 0, textBackgroundWidth, textBackgroundHeight, Color.WHITE);
				font.draw(batch, writerTextLayout, fontX, fontY + (font.getCapHeight()));
			}
			
			if(showFramePlusFps && (timer += Gdx.graphics.getDeltaTime()) > duration) showFramePlusFps = false;
			
			if(showFramePlusFps) {
				shapeDrawer.filledRectangle(0, 0 + textBackgroundHeight + 10, textBackgroundWidth, textBackgroundHeight, Color.WHITE);
			}
			
			batch.end();
			batch.setProjectionMatrix(oldProj);
			batch.setShader(oldShader);
			
			if(wasDrawing) batch.begin();
		}
		else if(isCapturing) {
			captureFrame();
		}
		
	}

	private void debugTouchBounds(float x, float y, float width, float height) {
		shapeDrawer.rectangle(x, y, width, height, Color.GREEN);
	}

}
