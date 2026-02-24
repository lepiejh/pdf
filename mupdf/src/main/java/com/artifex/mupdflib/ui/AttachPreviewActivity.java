package com.artifex.mupdflib.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RelativeLayout;

import com.artifex.mupdflib.FilePicker;
import com.artifex.mupdflib.MuPDFCore;
import com.artifex.mupdflib.MuPDFPageAdapter;
import com.artifex.mupdflib.MuPDFReaderView;
import com.ved.framework.base.BaseActivity;
import com.ved.framework.http.DownLoadManager;
import com.ved.framework.http.download.ProgressCallBack;
import com.ved.framework.permission.IPermission;
import com.ved.framework.utils.FileUtils;
import com.ved.framework.utils.KLog;
import com.ved.framework.utils.RegexUtils;
import com.ved.framework.utils.ToastUtils;
import com.ved.framework.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.leapinfo.mupdf.R;
import cn.leapinfo.mupdf.databinding.FragmentAttachPreviewBinding;
import okhttp3.ResponseBody;

/**
 * Created by Administrator on 2017/9/27.
 */

public class AttachPreviewActivity extends BaseActivity<FragmentAttachPreviewBinding, AttachPreviewViewModel> implements FilePicker.FilePickerSupport{
    private int attachFileType;
    private String attachFileUrl;
    private String attachFileName;

    private MuPDFCore muPDFCore;
    private MuPDFReaderView muPDFReaderView;
    public static final String ATTACH_FILE_URL = "ATTACH_FILE_URL";
    public static final String ATTACH_FILE_NAME = "ATTACH_FILE_NAME";
    public static final String ATTACH_FILE_PATH = "ATTACH_FILE_PATH";
    public static final String IS_ASSETS_FILE = "IS_ASSETS_FILE";
    public static final int IMAGE_FILE = 900;
    public static final int PDF_FILE = 800;
    private String destFileDir;
    private boolean isAssetsFile;
    private String tempAssetFilePath;

    public static void startActivity(Context context, String attachFileUrl) {
        startActivity(context, attachFileUrl, "", false);
    }

    public static void startActivity(Context context, String attachFileUrl, String attachFileName) {
        startActivity(context, attachFileUrl, attachFileName, false);
    }

    public static void startAssetsActivity(Context context, String assetsFileName) {
        startActivity(context, assetsFileName, assetsFileName, true);
    }

    public static void startActivity(Context context,String attachFileUrl, String attachFileName, boolean isAssets) {
        Bundle bundle = new Bundle();
        bundle.putString(ATTACH_FILE_URL, attachFileUrl);
        if (!TextUtils.isEmpty(attachFileName))bundle.putString(ATTACH_FILE_NAME, attachFileName);
        bundle.putBoolean(IS_ASSETS_FILE, isAssets);
        Intent intent = new Intent(context,AttachPreviewActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    @Override
    public void initData() {
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            attachFileUrl = bundle.getString(ATTACH_FILE_URL);
            attachFileName = bundle.getString(ATTACH_FILE_NAME);
            isAssetsFile = bundle.getBoolean(IS_ASSETS_FILE, false);
        }
        attachFileType = isPdfFile(attachFileUrl) ? PDF_FILE : IMAGE_FILE;

        getViewModel().title.set(TextUtils.isEmpty(attachFileName) ? "文书附件预览" : attachFileName);

        if (attachFileType == IMAGE_FILE) {
            getViewModel().pinchImageView.set(true);
            getViewModel().rlPdfContainer.set(false);

            getViewModel().attachFileUrl.set(attachFileUrl);

        } else if (attachFileType == PDF_FILE) {
            getViewModel().pinchImageView.set(false);
            getViewModel().rlPdfContainer.set(true);
        }
        destFileDir = Utils.getContext().getCacheDir().getPath()+File.separator+"document";

        if (Build.VERSION.SDK_INT >= 33) {
            requestAndroid13Permissions();
        } else {
            requestLegacyPermissions();
        }
    }

    private void requestAndroid13Permissions() {
        // Android 13+ 不需要存储权限来访问应用自己的目录
        // 但如果要读取其他应用的媒体文件，才需要这些权限
        List<String> permissions = new ArrayList<>();

        // 检查是否需要读取媒体文件（根据您的实际需求）
        if (attachFileType == IMAGE_FILE) {
            // 如果是图片，可能需要读取媒体权限
            permissions.add("android.permission.READ_MEDIA_IMAGES");
        }

        if (permissions.isEmpty()) {
            // 不需要权限，直接执行
            proceedWithoutPermission();
        } else {
            // 请求必要的权限
            requestPermission(new IPermission() {
                @Override
                public void onGranted() {
                    proceedWithoutPermission();
                }

                @Override
                public void onDenied(boolean denied) {
                    // 即使拒绝权限，仍然可以继续（因为我们可以使用应用专属目录）
                    KLog.i("部分功能可能受限，但PDF预览仍可进行");
                    proceedWithoutPermission();
                }
            }, permissions.toArray(new String[0]));
        }
    }

    // Android 12及以下版本权限请求
    private void requestLegacyPermissions() {
        requestPermission(new IPermission() {
            @Override
            public void onGranted() {
                proceedWithoutPermission();
            }

            @Override
            public void onDenied(boolean denied) {
                ToastUtils.showLong("请授权读写存储卡的权限，才能完成附件预览");
                finish();
            }
        }, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void proceedWithoutPermission() {
        if (isAssetsFile) {
            copyAssetsToTempAndOpen(attachFileUrl);
        } else if (!RegexUtils.isURL(attachFileUrl)) {
            // 本地文件路径
            File file = new File(attachFileUrl);
            if (file.exists()) {
                updatePdfFile(attachFileUrl);
            } else {
                ToastUtils.showShort("文件不存在");
                finish();
            }
        } else {
            downLoadFile();
        }
    }

    private void copyAssetsToTempAndOpen(String assetsFileName) {
        getViewModel().pbLoading.set(true);

        try {
            // 使用应用的缓存目录
            File tempDir = getCacheDir();
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // 创建临时文件
            String tempFileName = "pdf_asset_" + System.currentTimeMillis() + "_" + assetsFileName;
            File tempFile = new File(tempDir, tempFileName);

            // 复制文件
            InputStream is = getAssets().open(assetsFileName);
            FileOutputStream fos = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }

            fos.close();
            is.close();

            // 记录临时文件路径，用于后续清理
            tempAssetFilePath = tempFile.getAbsolutePath();

            // 打开PDF
            updatePdfFile(tempAssetFilePath);

        } catch (IOException e) {
            e.printStackTrace();
            ToastUtils.showShort("打开PDF文件失败");
            getViewModel().pbLoading.set(false);
            finish();
        }
    }

    private void downLoadFile() {
        String destFileName = System.currentTimeMillis() + ".document";
        DownLoadManager.getInstance().load(attachFileUrl, new ProgressCallBack<ResponseBody>(destFileDir, destFileName) {
            @Override
            public void onStart() {
                super.onStart();
                getViewModel().pbLoading.set(true);
            }

            @Override
            public void onCompleted() {
                getViewModel().pbLoading.set(false);
            }

            @Override
            public void onSuccess(ResponseBody responseBody) {
//                ToastUtils.showShort("文件下载完成！");
                getViewModel().pbLoading.set(false);
                updatePdfFile(destFileDir+File.separator+destFileName);
            }

            @Override
            public void progress(final long progress, final long total) {
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                ToastUtils.showShort("文件下载失败！");
                getViewModel().pbLoading.set(false);
            }
        });
    }

    public void updatePdfFile(String filePath) {
        if (!TextUtils.isEmpty(filePath)) {
            muPDFCore = openPdfFile(filePath);
            if (muPDFCore == null) {
                ToastUtils.showShort("打开PDF文件失败");
                finish();
                return;
            }
            int totalPageCount = muPDFCore.countPages();
            if (totalPageCount == 0) {
                ToastUtils.showShort("PDF文件格式错误");
                finish();
                return;
            }
            muPDFCore.setDisplayPages(1); //每页展示一张pdf

            muPDFReaderView = new MuPDFReaderView(this) {
                @Override
                protected void onMoveToChild(int i) {
                    super.onMoveToChild(i);
                    getViewModel().pageNumber.set(String.format(" %s / %s ", i + 1, totalPageCount));
                }

                @Override
                protected void onChildSetup(int i, View v) {
                    super.onChildSetup(i, v);
                    getViewModel().pbLoading.set(false);
                    getViewModel().tvPageNumber.set(true);
                }

            };
            muPDFReaderView.setAdapter(new MuPDFPageAdapter(this, this, muPDFCore));
            muPDFReaderView.setKeepScreenOn(true);
            muPDFReaderView.setScrollingDirectionHorizontal(true);

            binding.rlPdfContainer.addView(muPDFReaderView, new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT));
            getViewModel().pageNumber.set(String.format(" %s / %s ", 1, totalPageCount));
        } else {
            KLog.e("获取文件失败");
        }
    }

    public boolean isPdfFile(String fileUrl) {
        if (!TextUtils.isEmpty(fileUrl)) {
            return fileUrl.endsWith(".pdf") || fileUrl.endsWith(".PDF");
        }
        return false;
    }

    private MuPDFCore openPdfFile(String path) {
        try {
            muPDFCore = new MuPDFCore(this, path);
        } catch (Exception e) {
            KLog.e(e.getMessage());
            return null;
        }
        return muPDFCore;
    }

    @Override
    public void performPickFor(FilePicker picker) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FileUtils.deleteAllInDir(destFileDir);
        if (tempAssetFilePath != null) {
            File tempFile = new File(tempAssetFilePath);
            if (tempFile.exists()) {
                boolean deleted = tempFile.delete();
                KLog.i("清理assets临时文件: " + (deleted ? "成功" : "失败"));
            }
        }
    }

    @Override
    public int initContentView(Bundle savedInstanceState) {
        return R.layout.fragment_attach_preview;
    }
}
