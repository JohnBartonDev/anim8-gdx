package com.github.tommyettinger.anim8.recorder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.function.BiConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO.PNG;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.github.tommyettinger.anim8.AnimatedGif;
import com.github.tommyettinger.anim8.AnimatedPNG;

import space.earlygrey.shapedrawer.ShapeDrawer;

public class Anim8Gui extends InputAdapter {
	
	private enum RecorderState {
		NORMAL, 
		SCALE,
		MOVE
	}
	private RecorderState state = RecorderState.NORMAL;
	
	private static final int TOUCH_SIZE = 8;
	private static final int HALF_TOUCH_SIZE = TOUCH_SIZE / 2;
	
	private FileHandle outputDirectory;
	
	private boolean isActive = true;
	private boolean isWriting;
	private boolean isCapturing;
	private boolean hideGui;
	
	private int activateKey = Keys.GRAVE;
	private final int fullScreenKey = Keys.F;
	private final int scaleAllKey = Keys.SHIFT_LEFT;
	private final int moveAllKey = Keys.SPACE;
	private final int resetKey = 0;
	private final int startStopKey = Keys.S;
	
	private int minimumDistance = 10;
	
	private Vector2 touchPos;
	private Boundary leftBoundary;
	private Boundary rightBoundary;
	private Boundary topBoundary;
	private Boundary bottomBoundary;
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
	
	private Matrix4 oldProj;
	private Batch batch;
	private ShapeDrawer shapeDrawer;
	
	private boolean writeAnimatedGif;
	private boolean writeAnimatedPNG;
	private boolean writePNG8;
	
	private AnimatedGif animatedGif;
	private AnimatedPNG animatedPNG;
	private PNG png8;
	
	private Texture pixelTexture;
	
	private int frameWidth;
	private int frameHeight;
	
	private Date date;
	private DateTimeFormatter dateFormat;
	
	private static Texture createPixelTexture() {
		Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixmap.drawPixel(0, 0, 0xFFFFFFFF);
		Texture t = new Texture(pixmap);
		pixmap.dispose();
		return t;
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
		
		date = new Date();
		dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-h-mm-s-a");
		
		final int width = 100;
		final int height = 100;
		final int centerX = Gdx.graphics.getWidth() / 2;
		final int centerY = Gdx.graphics.getHeight() / 2;
		
		leftBoundary = new Boundary();
		rightBoundary = new Boundary();
		topBoundary = new Boundary();
		bottomBoundary = new Boundary();
		
		p1 = new Vector2(centerX - width / 2, centerY - height / 2);
		p2 = new Vector2(p1.x, p1.y + height);
		p3 = new Vector2(p1.x + width, p1.y + height);
		p4 = new Vector2(p1.x + width, p1.y);
		
		touchPos = new Vector2();
		
		constructBoundaries();
		
		oldProj = new Matrix4();
		frames = new Array<>(60);
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
	
	public void writeToFile() {
		if(isWriting || frames.size == 0) return;
		isWriting = true;
		
		final String fileName = createFileName();

		if(writeAnimatedGif) {
			if(customAnimatedGifWriter != null) {
				customAnimatedGifWriter.accept(animatedGif, frames);
			}
			else {
				try {
					animatedGif.write(Gdx.files.local(outputDirectory.path() + '/' + fileName + ".gif"), frames, 16);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
		clear();
	}
	
	public void captureFrame() {
		if(frames.size == 60) {
			stop();
			return;
		}
		frames.add(ScreenUtils.getFrameBufferPixmap((int)p1.x, (int)p1.y, frameWidth, frameHeight));
	}
	
	private String createFileName() {
		return dateFormat.format(LocalDateTime.now());
	}
	
	public void setCustomAnimatedGifWriter(BiConsumer<AnimatedGif, Array<Pixmap>> consumer) {
		customAnimatedGifWriter = consumer;
	}
	
	public void writeAnimatedGif(boolean write) {
		if(animatedGif == null) animatedGif = new AnimatedGif();
		writeAnimatedGif = write;
	}
	
	public void writeAnimatedPNG(boolean write) {
		writeAnimatedPNG = write;
	}
	
	private void clear() {
		isWriting = false;
		frames.clear();
		hideGui = false;
		state = RecorderState.NORMAL;
	}
	
	public void dispose() {
		if(pixelTexture != null) pixelTexture.dispose();
	}
	
	private void saveTemp() {
		p1Tmp.set(p1);
		p2Tmp.set(p2);
		p3Tmp.set(p3);
		p4Tmp.set(p4);
	}
	
	private void constructBoundaries() {
		leftBoundary.set(p1, p2);
		topBoundary.set(p2, p3);
		bottomBoundary.set(p1, p4);
		rightBoundary.set(p4, p3);
	}
	
	private void setSelected(boolean selected) {
		leftBoundary.isSelected = selected;
		rightBoundary.isSelected = selected;
		topBoundary.isSelected = selected;
		bottomBoundary.isSelected = selected;
	}
	
	private void updateTouchPos(int screenX, int screenY) {
		touchPos.set(screenX, Gdx.graphics.getHeight() - screenY);
	}
	
	private boolean isSelected(Vector2 p1, Vector2 p2, float x, float y) {
		if(p1.x == p2.x) {
			if(x >= (p1.x - HALF_TOUCH_SIZE) && x <= (p1.x + HALF_TOUCH_SIZE)) {
				if(y >= (p1.y - HALF_TOUCH_SIZE) && y <= (p2.y + HALF_TOUCH_SIZE)) {
					return true;
				}
			}
		}
		else {
			if(y >= (p1.y - HALF_TOUCH_SIZE) && y <= (p2.y + HALF_TOUCH_SIZE)) {
				if(x >= (p1.x - HALF_TOUCH_SIZE) && x <= (p2.x + HALF_TOUCH_SIZE)) {
					return true;
				}
			}
		}
		return false;
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
				state = RecorderState.SCALE;
				setSelected(true);
				saveTemp();
				updateTouchPos(Gdx.input.getX(), Gdx.input.getY());
				return true;
			case moveAllKey:
				state = RecorderState.MOVE;
				setSelected(true);
				saveTemp();
				updateTouchPos(Gdx.input.getX(), Gdx.input.getY());
				return true;
			case startStopKey:
				if(!isCapturing) {
					start();
				}
				else {
					stop();
				}
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
				if(state.equals(RecorderState.SCALE)) {
					setSelected(false);
					state = RecorderState.NORMAL;
				}
				return true;
			case moveAllKey:
				if(state.equals(RecorderState.MOVE)) {
					setSelected(false);
					state = RecorderState.NORMAL;
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
		constructBoundaries();
		
		if(state.equals(RecorderState.NORMAL)) {
			leftBoundary.isSelected = isSelected(p1, p2, touchPos.x, touchPos.y);
			rightBoundary.isSelected = isSelected(p4, p3, touchPos.x, touchPos.y);
			topBoundary.isSelected = isSelected(p2, p3, touchPos.x, touchPos.y);
			bottomBoundary.isSelected = isSelected(p1, p4, touchPos.x, touchPos.y);
		}
		return false;
	}
	
	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		if(!isActive || isWriting) return false;
		if(state.equals(RecorderState.NORMAL)) setSelected(false);
		return false;
	}
	
	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		if(!isActive || isWriting) return false;
		
		//Flip y
		screenY = Gdx.graphics.getHeight() - screenY;
		
		switch(state) {
			case NORMAL:
				if(leftBoundary.isSelected) {
					p1.x = p2.x = MathUtils.clamp(screenX, 0, p3.x - minimumDistance);
				}
				
				if(topBoundary.isSelected) {
					p2.y = p3.y = MathUtils.clamp(screenY, p1.y + minimumDistance, Gdx.graphics.getHeight());
				}
				
				if(bottomBoundary.isSelected) {
					p1.y = p4.y = MathUtils.clamp(screenY, 0, p2.y - minimumDistance);
				}
				
				if(rightBoundary.isSelected) {
					p3.x = p4.x = MathUtils.clamp(screenX, p1.x + minimumDistance, Gdx.graphics.getWidth());
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
				if((p4X - p1X) <= minimumDistance || (p2Y - p1Y) <= minimumDistance) return false;
				
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

	public void draw() {
		if(!isActive) return;

		if(!hideGui) {
			boolean wasDrawing = batch.isDrawing();
			
			//Flush whatever the user was currently drawing
			if(wasDrawing) batch.end();
			
			ShaderProgram oldShader = batch.getShader();
			
			batch.setShader(null);
			oldProj.set(batch.getProjectionMatrix());
			batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			batch.begin();

			//Draw boundaries
			leftBoundary.draw(shapeDrawer);
			rightBoundary.draw(shapeDrawer);
			topBoundary.draw(shapeDrawer);
			bottomBoundary.draw(shapeDrawer);
			
			//Draw points
			float size = 6;
			float offset = size / 2;
			shapeDrawer.filledRectangle(p1.x - offset, p1.y - offset, size, size, Color.BLACK);
			shapeDrawer.filledRectangle(p2.x - offset, p2.y - offset, size, size, Color.BLACK);
			shapeDrawer.filledRectangle(p3.x - offset, p3.y - offset, size, size, Color.BLACK);
			shapeDrawer.filledRectangle(p4.x - offset, p4.y - offset, size, size, Color.BLACK);
			
			batch.end();
			batch.setProjectionMatrix(oldProj);
			batch.setShader(oldShader);
			
			if(wasDrawing) batch.begin();
		}
		else if(isCapturing) {
			captureFrame();
		}
	}
	
	private class Boundary {

		private static final int SIZE = 2;
		private static final int SELECT_SIZE = 4;
		
		public boolean isSelected;
		private Vector2 p1;
		private Vector2 p2;

		public void set(Vector2 p1, Vector2 p2) {
			this.p1 = p1;
			this.p2 = p2;
		}

		public void debugTouchBounds(ShapeDrawer shapeDrawer) {
			//Vertical
			if(p1.x == p2.x) {
				float x = p1.x - HALF_TOUCH_SIZE;
				float y = p1.y - HALF_TOUCH_SIZE;
				float width = TOUCH_SIZE;
				float height = Math.abs(p1.y - p2.y) + TOUCH_SIZE;
				shapeDrawer.rectangle(x, y, width, height, isSelected ? Color.RED : Color.GREEN);
			}
			else {
				float x = p1.x - HALF_TOUCH_SIZE;
				float y = p1.y - HALF_TOUCH_SIZE;
				float width = Math.abs(p1.x - p2.x) + TOUCH_SIZE;
				float height = TOUCH_SIZE;
				shapeDrawer.rectangle(x, y, width, height, isSelected ? Color.RED : Color.GREEN);
			}
		}
		
		public void draw(ShapeDrawer shapeDrawer) {
			if(!isSelected) {
				shapeDrawer.line(p1.x, p1.y, p2.x, p2.y, Color.BLACK, SIZE);
			}
			else {
				shapeDrawer.line(p1, p2, Color.RED, SELECT_SIZE);
			}
		}
		
	}
	

}
