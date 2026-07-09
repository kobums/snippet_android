package com.gowoobro.snippet.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.ArrayList

/**
 * 시스템 공유 시트 실행 헬퍼.
 *
 * 스펙 출처: docs/native-migration/04-platform.md §5 (네이티브 매핑)
 * - Intent.ACTION_SEND (type image/png)
 * - EXTRA_STREAM = FileProvider Uri
 * - FLAG_GRANT_READ_URI_PERMISSION 부여
 * - Instagram 전용 인텐트는 스펙에 "현재 패리티 범위 아님"이므로 일반 공유시트만 구현
 */
object ShareSheet {

    /**
     * 이미지 공유 시트를 실행한다.
     *
     * @param context   Context
     * @param imageUri  FileProvider Uri (image/png)
     * @param shareText 공유 텍스트 (optional, Intent.EXTRA_TEXT)
     * @param chooserTitle 시스템 공유 시트 타이틀
     */
    fun shareImage(
        context: Context,
        imageUri: Uri,
        shareText: String = "Snippet으로 독서했어요",
        chooserTitle: String = "공유하기",
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * 여러 이미지를 한 번에 공유한다 (ACTION_SEND_MULTIPLE).
     * 메모 이미지 다중 페이지 내보내기에 사용.
     *
     * @param context      Context
     * @param imageUris    FileProvider Uri 목록 (image/png)
     * @param shareText    공유 텍스트 (optional)
     * @param chooserTitle 시스템 공유 시트 타이틀
     */
    fun shareImages(
        context: Context,
        imageUris: List<Uri>,
        shareText: String = "Snippet 메모를 공유합니다",
        chooserTitle: String = "공유하기",
    ) {
        if (imageUris.isEmpty()) return
        if (imageUris.size == 1) {
            shareImage(context, imageUris.first(), shareText, chooserTitle)
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
