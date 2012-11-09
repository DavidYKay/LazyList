package com.fedorvlasov.lazylist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.camtango.R;
import com.camtango.Constants.Photos;

public class ImageLoader {

	public MemoryCache memoryCache=new MemoryCache();
	public FileCache fileCache;
	private Map<ImageView, String> imageViews=Collections.synchronizedMap(new WeakHashMap<ImageView, String>());
	ExecutorService executorService; 
	private int attempts=0;
	//private boolean progress = false;

	public ImageLoader(Context context){
		fileCache=new FileCache(context);
		executorService=Executors.newFixedThreadPool(5);
	}

	final int stub_id=R.drawable.placeholder;
	final int stub_big=R.drawable.placeholder_big_noise;

	public void DisplayImage(String url, ImageView imageView, int required_size)
	{
		imageViews.put(imageView, url);
		Bitmap bitmap=memoryCache.get(url);
		if(bitmap!=null)
			imageView.setImageBitmap(bitmap);
		else if(url.startsWith("file://")){
			//image is local
			String filepath = url.substring(7);
			imageView.setImageBitmap(decodeFile( new File(filepath), required_size));
			//If file is not on disk, load from web.

		} else {
			File f=fileCache.getFile(url);

			//from SD cache
			Bitmap b = decodeFile(f, required_size);
			if(b!=null){
				imageView.setImageBitmap(b);
			} else {
				queuePhoto(url, imageView, required_size);
				if(required_size==Photos.FULL_SIZE){
					imageView.setImageResource(stub_big);
				} else{
					imageView.setImageResource(stub_id);
				}
				Bitmap bmp = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
				imageView.setImageBitmap(Bitmap.createScaledBitmap(bmp, required_size, required_size, false));
			}
		}
	}
	public void DisplayImage(String url, ImageView imageView, int required_size, boolean progress){
		imageViews.put(imageView, url);
		Bitmap bitmap=memoryCache.get(url);
		if(bitmap!=null)
			imageView.setImageBitmap(bitmap);
		else if(url.startsWith("file://")){
			//image is local
			String filepath = url.substring(7);
			imageView.setImageBitmap(decodeFile( new File(filepath), required_size));
			//If file is not on disk, load from web.

		}
		else
		{
			File f=fileCache.getFile(url);

			//from SD cache
			Bitmap b = decodeFile(f, required_size);
			if(b!=null){
				imageView.setImageBitmap(b);
			} else {
				queuePhoto(url, imageView, required_size, progress);
				if(required_size==Photos.FULL_SIZE){
					imageView.setImageResource(stub_big);
				} else{
					imageView.setImageResource(stub_id);
				}
				//this.progress=progress;
				if(progress){
					ProgressBar prog = (ProgressBar) ((View) ((View) imageView).getParent()).findViewById(R.id.progressBar1);
					prog.setVisibility(View.VISIBLE);
				}

				Bitmap bmp = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
				imageView.setImageBitmap(Bitmap.createScaledBitmap(bmp, required_size, required_size, false));
			}

		}
	}

	private void queuePhoto(String url, ImageView imageView, int required_size)
	{
		PhotoToLoad p=new PhotoToLoad(url, imageView, required_size);
		executorService.submit(new PhotosLoader(p));
	}
	
	private void queuePhoto(String url, ImageView imageView, int required_size, boolean progress)
	{
		PhotoToLoad p=new PhotoToLoad(url, imageView, required_size, progress);
		executorService.submit(new PhotosLoader(p));
	}

	private Bitmap getBitmap(String url, int required_size) 
	{
		File f=fileCache.getFile(url);
		//from web
		try {
			return getBitmapFromWeb(url, f, required_size);
		} catch (Exception ex){
			ex.printStackTrace();
			Log.d("ImageLoader", "Image loader error!");
			return null;
		}
	}

	private Bitmap getBitmapFromWeb(String url, File f, int required_size) throws IOException{
		Log.d("ImageLoader", "loading image from web");
		Bitmap bitmap=null;
		URL imageUrl = new URL(url);
		HttpURLConnection conn = (HttpURLConnection)imageUrl.openConnection();
		conn.setConnectTimeout(30000);
		conn.setReadTimeout(30000);
		conn.setInstanceFollowRedirects(true);
		InputStream is=conn.getInputStream();
		OutputStream os = new FileOutputStream(f);
		Utils.CopyStream(is, os);
		os.close();
		bitmap = decodeFile(f, required_size);
		if(bitmap==null){
			if(attempts<5){
				attempts++;
				return getBitmapFromWeb(url, f, required_size);
			} else {
				return null;
			}
		}
		return bitmap;
	}

	//decodes image and scales it to reduce memory consumption
	private Bitmap decodeFile(File f, int required_size){
		try {
			//decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new FileInputStream(f),null,o);

			//Find the correct scale value. It should be the power of 2.
			int width_tmp=o.outWidth, height_tmp=o.outHeight;

			if(width_tmp != required_size){

				/*int scale=1;
                while(true){
                    if(width_tmp/2<required_size || height_tmp/2<required_size)
                        break;
                    width_tmp/=2;
                    height_tmp/=2;
                    scale*=2;
                }*/
				int scale = (int) Math.floor((float)height_tmp / (float)required_size);

				//decode with inSampleSize
				BitmapFactory.Options o2 = new BitmapFactory.Options();
				o2.inSampleSize=scale;
				o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
				o2.inDither = true;
				Bitmap bm = BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
				if(bm==null){
					return null;
				}
				return Bitmap.createScaledBitmap(bm, required_size, required_size, true);
			} else {
				return BitmapFactory.decodeStream(new FileInputStream(f), null, null);
			}


		} catch (FileNotFoundException e) {}
		return null;
	}

	//Task for the queue
	private class PhotoToLoad
	{
		public String url;
		public ImageView imageView;
		public int required_size;
		boolean progress = false;

		public PhotoToLoad(String u, ImageView i, int r){
			url=u; 
			imageView=i;
			required_size = r;
		}
		
		public PhotoToLoad(String u, ImageView i, int r, boolean t){
			url=u; 
			imageView=i;
			required_size = r;
			progress = t;
		}
	}

	class PhotosLoader implements Runnable {
		PhotoToLoad photoToLoad;
		PhotosLoader(PhotoToLoad photoToLoad){
			this.photoToLoad=photoToLoad;
		}

		@Override
		public void run() {
			if(imageViewReused(photoToLoad))
				return;
			Bitmap bmp=getBitmap(photoToLoad.url, photoToLoad.required_size);
			memoryCache.put(photoToLoad.url, bmp);
			if(imageViewReused(photoToLoad))
				return;
			BitmapDisplayer bd=new BitmapDisplayer(bmp, photoToLoad, photoToLoad.progress);
			Activity a=(Activity)photoToLoad.imageView.getContext();
			a.runOnUiThread(bd);
		}
	}

	boolean imageViewReused(PhotoToLoad photoToLoad){
		String tag=imageViews.get(photoToLoad.imageView);
		if(tag==null || !tag.equals(photoToLoad.url))
			return true;
		return false;
	}

	//Used to display bitmap in the UI thread
	class BitmapDisplayer implements Runnable
	{
		Bitmap bitmap;
		PhotoToLoad photoToLoad;
		boolean progress = false;
		public BitmapDisplayer(Bitmap b, PhotoToLoad p, boolean t){bitmap=b;photoToLoad=p;progress=t;}
		public void run()
		{
			if(progress){
				ProgressBar prog = (ProgressBar) ((View) ((View) photoToLoad.imageView).getParent()).findViewById(R.id.progressBar1);
				prog.setVisibility(View.GONE);
			}
			if(imageViewReused(photoToLoad))
				return;
			if(bitmap!=null)
				photoToLoad.imageView.setImageBitmap(bitmap);
			//else
			//photoToLoad.imageView.setImageResource(stub_id);
		}
	}

	public void clearCache() {
		memoryCache.clear();
		fileCache.clear();
	}

}
