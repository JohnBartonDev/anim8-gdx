package com.github.tommyettinger;

import java.io.IOException;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.github.tommyettinger.anim8.AnimatedGif;
import com.github.tommyettinger.anim8.recorder.Anim8Gui;

import space.earlygrey.shapedrawer.ShapeDrawer;

public class RecorderTest extends ApplicationAdapter {
	
	private Batch batch;
	private Texture pixelTexture;
	private ShapeDrawer shapeDrawer;
	private Anim8Gui recorder;
	private AnimatedGif gif;
	
	@Override
	public void create() {
		batch = new SpriteBatch();
		
		Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pix.drawPixel(0, 0, 0xFFFFFFFF);
		pixelTexture = new Texture(pix);
		
		shapeDrawer = new ShapeDrawer(batch, new TextureRegion(pixelTexture));
		
		recorder = new Anim8Gui(batch);
		
		gif = new AnimatedGif();
		
		recorder.setCustomWriteConsumer((frames) -> {
			try {
				gif.write(Gdx.files.local("images/AnimatedPNGTest-" + ".gif"), frames, 16);
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		});
		
		Gdx.input.setInputProcessor(recorder);
	}
	
	@Override
	public void resize(int width, int height) {
		batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
	}
	
	@Override
	public void render() {
		Gdx.gl.glClearColor(1f, 1f, 1f, 0f);
		Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
		
		batch.begin();
		recorder.draw(shapeDrawer);
		batch.end();
		
	}
	
	@Override
	public void dispose() {
		batch.dispose();
		pixelTexture.dispose();
	}
	
	public static void main(String[] args) {
		Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
		configuration.setTitle("GUI");
		configuration.setWindowedMode(500, 500);
		
		new Lwjgl3Application(new RecorderTest(), configuration);
	}

}
