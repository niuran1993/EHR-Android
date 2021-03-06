/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.philit.ehr.view;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.philit.ehr.adapter.BinderAdapter;

/**
 * Actual View Binder implementation class.
 */
public class BinderView extends FrameLayout {

	// Static values for current flip mode.
	private static final int FLIP_NEXT = 0;
	private static final int FLIP_NONE = 1;
	private static final int FLIP_PREV = 2;
	
	// Fragment Shader.
	private static final String SHADER_FRAGMENT = "\r\n"
			+ "precision mediump float;                             \r\n"
			+ "uniform sampler2D sTop;                              \r\n"
			+ "uniform sampler2D sBottom;                           \r\n"
			+ "uniform float uniformY;                              \r\n"
			+ "varying vec2 vPos;                                   \r\n"
			+ "void main() {                                        \r\n"
			+ "  if (vPos.y >= 0.0 && vPos.y > uniformY) {          \r\n"
			+ "    vec2 tPos = vec2(vPos.x, -vPos.y) * 0.5 + 0.5;   \r\n"
			+ "    gl_FragColor = texture2D(sTop, tPos);            \r\n"
			+ "    float c = max(0.0, uniformY);                    \r\n"
			+ "    gl_FragColor *= mix(1.0, 0.5, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "  else if (vPos.y < 0.0 && vPos.y < uniformY) {      \r\n"
			+ "    vec2 tPos = vec2(vPos.x, -vPos.y) * 0.5 + 0.5;   \r\n"
			+ "    gl_FragColor = texture2D(sBottom, tPos);         \r\n"
			+ "    float c = min(1.0, 1.0 + uniformY);              \r\n"
			+ "    gl_FragColor *= mix(0.5, 1.0, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "  else if (vPos.y >= 0.0) {                          \r\n"
			+ "    float vy = -vPos.y / uniformY;                   \r\n"
			+ "    vec2 tPos = vec2(vPos.x, vy);                    \r\n"
			+ "    tPos.x += (1.0 - uniformY) * 0.5 * vPos.x * vy;  \r\n"
			+ "    tPos = tPos * 0.5 + 0.5;                         \r\n"
			+ "    gl_FragColor = texture2D(sBottom, tPos);         \r\n"
			+ "    float c = max(0.0, uniformY);                    \r\n"
			+ "    gl_FragColor *= mix(0.5, 1.0, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "  else if (vPos.y < 0.0) {                           \r\n"
			+ "    float vy = -vPos.y / uniformY;                   \r\n"
			+ "    vec2 tPos = vec2(vPos.x, -vy);                   \r\n"
			+ "    tPos.x += (1.0 + uniformY) * 0.5 * vPos.x * vy;  \r\n"
			+ "    tPos = tPos * 0.5 + 0.5;                         \r\n"
			+ "    gl_FragColor = texture2D(sTop, tPos);            \r\n"
			+ "    float c = min(1.0, 1.0 + uniformY);              \r\n"
			+ "    gl_FragColor *= mix(1.0, 0.5, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "}                                                    \r\n";

	// Vertex Shader.
	private static final String SHADER_VERTEX = "\r\n"
			+ "attribute vec2 aPos;                                 \r\n"
			+ "varying vec2 vPos;                                   \r\n"
			+ "void main() {                                        \r\n"
			+ "  gl_Position = vec4(aPos, 0.0, 1.0);                \r\n"
			+ "  vPos = aPos;                                       \r\n"
			+ "}                                                    \r\n";

	private int mFlipMode = FLIP_NONE;
	private Renderer mRenderer = new Renderer();
	private int mViewChildIndex = 0;
	private List<View> mViewChildren = new ArrayList<View>();
	private BinderAdapter adapter;
	private GLSurfaceView mViewRenderer;
	private boolean isMoving, isAutoFlip, hasHeadView, hasFootView,isClick,isRecycling; //是否正在手动移动，是否自动翻页，mViewChildren是否有头部页面，mViewChildren是否有尾部页面
	private Direction direction;
	public enum Direction{
		DIRECTION_DOWN,
		DIRECTION_UP
	}
	/**
	 * Default constructor.
	 */
	public BinderView(Context context) {
		super(context);
		init(context);
	}
	
	/**
	 * Default constructor.
	 */
	public BinderView(Context context, BinderAdapter adapter) {
		super(context);
		this.adapter = adapter;
		this.mViewChildren = adapter.getList();
		init(context);
	}

	/**
	 * Default constructor.
	 */
	public BinderView(Context context, AttributeSet attrs, BinderAdapter adapter) {
		super(context, attrs);
		this.adapter = adapter;
		this.mViewChildren = adapter.getList();
		init(context);
	}

	/**
	 * Default constructor.
	 */
	public BinderView(Context context, AttributeSet attrs, int defStyle, BinderAdapter adapter) {
		super(context, attrs, defStyle);
		this.adapter = adapter;
		this.mViewChildren = adapter.getList();
		init(context);
	}

	/**
	 * Initializes renderer view for page transitions - plus hook view for
	 * grabbing touch events in front of actual view.
	 */
	private void init(Context context) {
		mViewRenderer = new GLSurfaceView(context);
		mViewRenderer.setEGLContextClientVersion(2);
		mViewRenderer.setRenderer(mRenderer);
		mViewRenderer.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		
		LayoutParams params = new LayoutParams(
				android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.MATCH_PARENT);
		addView(mViewRenderer, params);
		setDirection(Direction.DIRECTION_UP); //默认为上滚方向
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float my = event.getY() * 2;
		
		//如果自动翻页
		if (isAutoFlip) {
			Log.i("BinderView", "autoFlip-onTouchEvent");
			switch (event.getAction()) {

			case MotionEvent.ACTION_DOWN:
				isMoving = false;
				isClick = true;
				/*switch (direction) {
				case DIRECTION_DOWN:
					if (my < getHeight() && mViewChildIndex >= 0) {
						mFlipMode = FLIP_PREV;
						updateRendererBitmaps();
						mViewRenderer.bringToFront();
						mRenderer.setFlipPosition(1f);
						invalidate();
					}
					break;
	
				default:
					if (my > getHeight() && mViewChildIndex <= mViewChildren.size() - 1) {
						mFlipMode = FLIP_NEXT;
						updateRendererBitmaps();
						mViewRenderer.bringToFront();
						mRenderer.setFlipPosition(-1f);
						invalidate();
					}
					break;
				}*/
				break;
	
			case MotionEvent.ACTION_MOVE:
				isMoving = true;
				isClick = false;
				/*if (mFlipMode != FLIP_NONE) {
					float fp = (getHeight() - my) / getHeight();
					fp = Math.min(1f, Math.max(-1f, fp));
					mRenderer.moveFlipPosition(fp);
				}*/
				break;
	
			case MotionEvent.ACTION_UP:
				isMoving = false;
				/*switch (direction) {
				case DIRECTION_DOWN:
					mViewChildIndex = mViewChildren.size() - 1;
					if (my > getHeight() && mFlipMode == FLIP_PREV) {
						swapViewOrder();
					}
					break;
	
				default:
					mViewChildIndex = 0;
					if (my < getHeight() && mFlipMode == FLIP_NEXT) {
						swapViewOrder();
					}
					break;
				}
				mFlipMode = FLIP_NONE;
				mRenderer.moveFlipPosition(my < getHeight() ? 1f : -1f);*/
				if (my < getHeight()) {
					autoFlip();
				}
				break;
			}
		}else {  //手动翻页
			Log.i("BinderView", "handFlip-onTouchEvent");
			switch (event.getAction()) {

			case MotionEvent.ACTION_DOWN:
				isMoving = false;
				isClick = true;
				if (my < getHeight() && mViewChildIndex >= 0) {
					mFlipMode = FLIP_PREV;
					updateRendererBitmaps();
					mViewRenderer.bringToFront();
					mRenderer.setFlipPosition(1f);
					invalidate();
				}else if (my > getHeight() && mViewChildIndex <= mViewChildren.size() - 1) {
					mFlipMode = FLIP_NEXT;
					updateRendererBitmaps();
					mViewRenderer.bringToFront();
					mRenderer.setFlipPosition(-1f);
					invalidate();
				}
				break;
	
			case MotionEvent.ACTION_MOVE:
				isMoving = true;
				isClick = false;
				if (mFlipMode != FLIP_NONE) {
					float fp = (getHeight() - my) / getHeight();
					if ((!hasHeadView && mViewChildIndex == 0) || (hasHeadView && mViewChildIndex == 1)) {
						//目前这里还存在bug，第2个页面会被当成第1个页面处理，翻不过来
						if (my > getHeight()) {
							fp = Math.max(0.2f, Math.max(-1f, fp));
						}else {
							fp = Math.min(1f, Math.max(-1f, fp));
						}
					}
					if((!hasFootView && mViewChildIndex == mViewChildren.size() - 1) || (hasFootView && mViewChildIndex == mViewChildren.size() - 2)){
						//目前这里还存在bug，类似
						if (my < getHeight()) {
							fp = Math.min(-0.2f, Math.max(-1f, fp));
						}else {
							fp = Math.min(1f, Math.max(-1f, fp));
						}
					}else {
						fp = Math.min(1f, Math.max(-1f, fp));
					}
					mRenderer.moveFlipPosition(fp);
				}
				break;
	
			case MotionEvent.ACTION_UP:
				isMoving = false;
				if (my > getHeight() && mFlipMode == FLIP_PREV && mViewChildIndex >= 0) {
					mViewChildIndex--;
				}else if (my < getHeight() && mFlipMode == FLIP_NEXT && mViewChildIndex <= mViewChildren.size() - 1) {
					mViewChildIndex++;
				}
				mFlipMode = FLIP_NONE;
				if (mViewChildIndex == -1) {
					mViewChildIndex = 0;
					mRenderer.moveFlipPosition(1f);
				}else if(mViewChildIndex == mViewChildren.size()){
					mViewChildIndex = mViewChildren.size() - 1;
					mRenderer.moveFlipPosition(-1f);
				}else if (hasHeadView && mViewChildIndex == 0) {
					mViewChildIndex = 1;
					mRenderer.moveFlipPosition(1f);
				}else if(hasFootView && mViewChildIndex == mViewChildren.size() - 1){
					mViewChildIndex = mViewChildren.size() - 2;
					mRenderer.moveFlipPosition(-1f);
				}else {
					mRenderer.moveFlipPosition(my < getHeight() ? 1f : -1f);
				}
				break;
			}
		}
		
		return super.onTouchEvent(event);
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			Log.i("BinderView", "onInterceptTouchEvent-ACTION_DOWN");
			break;

		case MotionEvent.ACTION_MOVE:
			Log.i("BinderView", "onInterceptTouchEvent-ACTION_MOVE");
			break;
			
		case MotionEvent.ACTION_UP:
			Log.i("BinderView", "onInterceptTouchEvent-ACTION_UP");
			break;
		}
		return true; //这里很关键，否则其上一级viewGroup将无法接收到move事件
	}
	
	@Override
	public boolean performClick() {
		Log.i("BinderView", "performClick");
		if (isClick) {
			mViewRenderer.performClick();
			return super.performClick();
		}
		return false;
	}
	
	/**
	 * 自动翻页
	 */
	protected void autoFlip() {
		Log.i("BinderView", "autoFlip");
		if (!isMoving) {
			//isAutoFlip = true; //自动翻页
			
			switch (direction) {
			case DIRECTION_DOWN:
				mFlipMode = FLIP_PREV;
				updateRendererBitmaps();
				mViewRenderer.bringToFront();
				mRenderer.setFlipPosition(1f);
				invalidate();
				
				mViewChildIndex = mViewChildren.size() - 1;
				swapViewOrder();
				mFlipMode = FLIP_NONE;
				mRenderer.moveFlipPosition(-1f);
				break;
			default:
				mFlipMode = FLIP_NEXT;	
				updateRendererBitmaps();
				mViewRenderer.bringToFront();
				mRenderer.setFlipPosition(-1f);
				invalidate();
				
				mViewChildIndex = 0;
				swapViewOrder();
				mFlipMode = FLIP_NONE;
				mRenderer.moveFlipPosition(1f);
				break;
			}
		}
	}
	
	protected void swapViewOrder() {
		if (mViewChildIndex < mViewChildren.size()) {
			View tempView = null;
			switch (direction) {
			case DIRECTION_DOWN:
				tempView = mViewChildren.get(mViewChildren.size() - 1);
				for (int i = mViewChildren.size() - 1; i > 0; i--) {
					mViewChildren.set(i, mViewChildren.get(i-1));			
				}
				mViewChildren.set(0, tempView);
				break;
	
			default:
				tempView = mViewChildren.get(0);
				for (int i = 0; i < mViewChildren.size() - 1; i++) {
					mViewChildren.set(i, mViewChildren.get(i+1));			
				}
				mViewChildren.set(mViewChildren.size() - 1, tempView);
				break;
			}
		}
	}

	/**
	 * 回收资源
	 */
	public void recycle() {
		isRecycling = true;
		List<Bitmap> bitmaps = adapter.getBitmaps();
		for (int i = 0; i < bitmaps.size(); i++) {
			Bitmap bitmap = bitmaps.get(i);
			if (bitmap != null && !bitmap.isRecycled()) {
				bitmap.recycle();
				bitmap = null;
			}
		}
		for (int j = 0; j < mViewChildren.size(); j++) {
			mViewChildren.remove(j);
		}
		System.gc();
	}
	
	public List<View> getmViewChildren() {
		return mViewChildren;
	}

	public void setmViewChildren(List<View> mViewChildren) {
		this.mViewChildren = mViewChildren;
	}

	public Direction getDirection() {
		return direction;
	}

	/**
	 * 自动翻页方向（手动翻页时不需要设置）
	 * @param direction
	 */
	public void setDirection(Direction direction) {
		this.direction = direction;
		//考虑到设置direction属性和hasHeadView属性顺序的影响，这里也应该做判断
		if (hasHeadView) {
			mViewChildIndex = 1;
		}else {
			if (direction == Direction.DIRECTION_DOWN) {
				mViewChildIndex = mViewChildren.size() - 1;
			}else {
				mViewChildIndex = 0;
			}
		}
		setCurrentView(mViewChildIndex);
	}

	public boolean isHasHeadView() {
		return hasHeadView;
	}
	
	/**
	 * 手动翻页时可以设置是否将list的第一个view设置为头部页面（自动翻页时不需要设置）
	 * @param hasHeadView
	 */
	public void setHasHeadView(boolean hasHeadView) {
		this.hasHeadView = hasHeadView;
		if (hasHeadView) {
			mViewChildIndex = 1;
			setCurrentView(mViewChildIndex);
		}
	}

	public boolean isHasFootView() {
		return hasFootView;
	}
	
	/**
	 * 手动翻页时可以设置是否将list的最后一个view设置为尾部页面（自动翻页时不需要设置）
	 * @param hasFootView
	 */
	public void setHasFootView(boolean hasFootView) {
		this.hasFootView = hasFootView;
	}

	public boolean isMoving() {
		return isMoving;
	}

	public void setMoving(boolean isMoving) {
		this.isMoving = isMoving;
	}

	public boolean isAutoFlip() {
		return isAutoFlip;
	}

	public void setAutoFlip(boolean isAutoFlip) {
		this.isAutoFlip = isAutoFlip;
	}

	/**
	 * Setter for current visible View.
	 */
	public void setCurrentView(int index) {
		if (index >= 0 && index < mViewChildren.size()) {
			setViewVisibility(mViewChildren.get(index), View.VISIBLE);
			mViewChildren.get(index).bringToFront();
		}
		if (index > 0) {
			setViewVisibility(mViewChildren.get(index - 1), View.INVISIBLE);
		}
		if (index < mViewChildren.size() - 1) {
			setViewVisibility(mViewChildren.get(index + 1), View.INVISIBLE);
		}

		for (int i = 0; i < index - 1; ++i) {
			removeView(mViewChildren.get(i));
		}
		for (int i = index + 2; i < mViewChildren.size(); ++i) {
			removeView(mViewChildren.get(i));
		}

		invalidate();
	}
	
	/**
	 * 这个方法不是很通用，只是为了这个项目的需求而特殊写的
	 */
	public void bringTitleToFront() {
		if (mViewChildIndex < mViewChildren.size()) {
			mViewChildIndex = 0;
			if (mViewChildren.get(0) instanceof ImageView) {
				swapViewOrder();
				isMoving = false;
			}
			post(new Runnable() {
				@Override
				public void run() {
					setCurrentView(mViewChildIndex);
				}
			});
		}
	}

	/**
	 * Changes requested view visibility.
	 */
	private void setViewVisibility(View view, int visibility) {
		view.setVisibility(visibility);
		// View is already attached.
		if (indexOfChild(view) >= 0) {
			return;
		}
		// otherwise add view to ViewGroup.
		addView(view, 0);
	}

	/**
	 * Updates renderer Bitmaps.
	 */
	private void updateRendererBitmaps() {
		if (mViewChildIndex < mViewChildren.size()) {
			// Generate two offscreen bitmaps.
			if (getHeight() > 0) {
				Bitmap top = Bitmap.createBitmap(getWidth(), getHeight(),
						Bitmap.Config.ARGB_8888);
				Bitmap bottom = Bitmap.createBitmap(getWidth(), getHeight(),
						Bitmap.Config.ARGB_8888);
				
				if (mFlipMode == FLIP_NEXT) {
					Canvas c = new Canvas(top);
					if (!isRecycling)
						mViewChildren.get(mViewChildIndex).draw(c);
				}
				if (mFlipMode == FLIP_NEXT
						&& mViewChildIndex < mViewChildren.size() - 1) {
					Canvas c = new Canvas(bottom);
					if (!isRecycling)
						mViewChildren.get(mViewChildIndex + 1).draw(c);
				}
				
				if (mFlipMode == FLIP_PREV) {
					Canvas c = new Canvas(bottom);
					if (!isRecycling)
						mViewChildren.get(mViewChildIndex).draw(c);
				}
				if (mFlipMode == FLIP_PREV && mViewChildIndex > 0) {
					Canvas c = new Canvas(top);
					if (!isRecycling)
						mViewChildren.get(mViewChildIndex - 1).draw(c);
				}
				
				mRenderer.setBitmaps(top, bottom);
			}
		}
	}

	/**
	 * Private renderer class.
	 */
	private class Renderer implements GLSurfaceView.Renderer {

		private Bitmap mBitmapTop, mBitmapBottom;
		private ByteBuffer mCoords;
		private float mFlipPosition;
		private float mFlipPositionTarget;
		private long mLastRenderTime;
		private int mProgram;
		private int[] mTextureIds;

		/**
		 * Default constructor.
		 */
		public Renderer() {
			final byte[] COORDS = { -1, 1, -1, -1, 1, 1, 1, -1 };
			mCoords = ByteBuffer.allocateDirect(8);
			mCoords.put(COORDS).position(0);
		}

		/**
		 * Private shader loader.
		 */
		private final int loadProgram(String vs, String fs) throws Exception {
			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vs);
			int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
			int program = GLES20.glCreateProgram();
			if (program != 0) {
				GLES20.glAttachShader(program, vertexShader);
				GLES20.glAttachShader(program, fragmentShader);
				GLES20.glLinkProgram(program);
				int[] linkStatus = new int[1];
				GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS,
						linkStatus, 0);
				if (linkStatus[0] != GLES20.GL_TRUE) {
					String error = GLES20.glGetProgramInfoLog(program);
					GLES20.glDeleteProgram(program);
					throw new Exception(error);
				}
			}
			return program;
		}

		/**
		 * Private shader loader/compiler.
		 */
		private final int loadShader(int shaderType, String source)
				throws Exception {
			int shader = GLES20.glCreateShader(shaderType);
			if (shader != 0) {
				GLES20.glShaderSource(shader, source);
				GLES20.glCompileShader(shader);
				int[] compiled = new int[1];
				GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS,
						compiled, 0);
				if (compiled[0] == 0) {
					String error = GLES20.glGetShaderInfoLog(shader);
					GLES20.glDeleteShader(shader);
					throw new Exception(error);
				}
			}
			return shader;
		}

		/**
		 * Animates flip position to requested position.
		 */
		public void moveFlipPosition(float posY) {
			mFlipPositionTarget = posY;
			mViewRenderer.requestRender();
		}

		@Override
		public void onDrawFrame(GL10 unused) {

			// Disable unneeded rendering flags.
			GLES20.glDisable(GLES20.GL_DEPTH_TEST);
			GLES20.glDisable(GLES20.GL_CULL_FACE);

			// Allocate new texture ids if needed.
			if (mTextureIds == null) {
				mTextureIds = new int[2];
				GLES20.glGenTextures(2, mTextureIds, 0);
				for (int textureId : mTextureIds) {
					// Set texture attributes.
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
				}
			}

			// If we have new Bitmaps.
			if (mBitmapTop != null) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[0]);
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmapTop, 0);
				mBitmapTop.recycle();
				mBitmapTop = null;
			}
			if (mBitmapBottom != null) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[1]);
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmapBottom, 0);
				mBitmapBottom.recycle();
				mBitmapBottom = null;
			}

			// Use our vertex/fragment shader program.
			GLES20.glUseProgram(mProgram);
			// Fetch variable ids.
			int uniformY = GLES20.glGetUniformLocation(mProgram, "uniformY");
			int sTop = GLES20.glGetUniformLocation(mProgram, "sTop");
			int sBottom = GLES20.glGetUniformLocation(mProgram, "sBottom");
			int aPos = GLES20.glGetAttribLocation(mProgram, "aPos");

			// If there's room for animation.
			if (Math.abs(mFlipPosition - mFlipPositionTarget) > 0.01f) {
				long currentTime = SystemClock.uptimeMillis();
				float t = Math.min(1f, (currentTime - mLastRenderTime) * .015f);
				mFlipPosition = mFlipPosition
						+ (mFlipPositionTarget - mFlipPosition) * t;
				mLastRenderTime = currentTime;
				mViewRenderer.requestRender();
			}
			// If we're done with animation plus user left us with touch up
			// event.
			else if (mFlipMode == FLIP_NONE) {
				post(new Runnable() {
					@Override
					public void run() {
						setCurrentView(mViewChildIndex);
					}
				});
			}

			// Set flip position variable.
			GLES20.glUniform1f(uniformY, mFlipPosition);
			// Set texture variables.
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[0]);
			GLES20.glUniform1i(sTop, 0);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[1]);
			GLES20.glUniform1i(sBottom, 1);
			// Set vertex position variables.
			GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_BYTE, false, 0,
					mCoords);
			GLES20.glEnableVertexAttribArray(aPos);
			// Render quad.
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		@Override
		public void onSurfaceChanged(GL10 unused, int width, int height) {
			// All we have to do is set viewport.
			GLES20.glViewport(0, 0, width, height);
		}

		@Override
		public void onSurfaceCreated(GL10 unused, EGLConfig config) {
			try {
				// Force instantiation for new texture ids.
				mTextureIds = null;
				// Load vertex/fragment shader program.
				mProgram = loadProgram(SHADER_VERTEX, SHADER_FRAGMENT);
			} catch (final Exception ex) {
				// On error show Toast.
				post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getContext(), ex.toString(),
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		}
		
		

		/**
		 * Setter for Bitmaps.
		 */
		public void setBitmaps(Bitmap top, Bitmap bottom) {
			if (top != null) {
				mBitmapTop = top;
			}
			if (bottom != null) {
				mBitmapBottom = bottom;
			}
		}

		/**
		 * Setter for flip position, value between [-1, 1].
		 */
		public void setFlipPosition(float posY) {
			mFlipPosition = posY;
			mFlipPositionTarget = posY;
			mLastRenderTime = SystemClock.uptimeMillis();
			mViewRenderer.requestRender();
		}
	}
}
