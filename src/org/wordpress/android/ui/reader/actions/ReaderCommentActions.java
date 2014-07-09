package org.wordpress.android.ui.reader.actions;

import android.os.Handler;
import android.text.TextUtils;

import com.android.volley.VolleyError;
import com.wordpress.rest.RestRequest;

import org.json.JSONObject;
import org.wordpress.android.WordPress;
import org.wordpress.android.datasets.ReaderCommentTable;
import org.wordpress.android.datasets.ReaderLikeTable;
import org.wordpress.android.datasets.ReaderUserTable;
import org.wordpress.android.models.ReaderComment;
import org.wordpress.android.models.ReaderCommentList;
import org.wordpress.android.models.ReaderPost;
import org.wordpress.android.models.ReaderUser;
import org.wordpress.android.ui.reader.ReaderConstants;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.VolleyUtils;

import java.util.HashMap;
import java.util.Map;

public class ReaderCommentActions {
    /**
     * get the latest comments for this post
     **/
    public static void updateCommentsForPost(final ReaderPost post,
                                             boolean applyOffset,
                                             final ReaderActions.UpdateResultListener resultListener) {
        String path = "sites/" + post.blogId + "/posts/" + post.postId + "/replies/"
                    + "?number=" + Integer.toString(ReaderConstants.READER_MAX_COMMENTS_TO_REQUEST)
                    + "&meta=likes";

        // get older comments first - subsequent calls to this routine will get newer ones if they exist
        path += "&order=ASC";

        // offset by the number of comments already stored locally (so we only get new comments)
        if (applyOffset) {
            int numLocalComments = ReaderCommentTable.getNumCommentsForPost(post);
            if (numLocalComments > 0) {
                path += "&offset=" + Integer.toString(numLocalComments);
            }
        }

        com.wordpress.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                handleUpdateCommentsResponse(jsonObject, post.blogId, resultListener);
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(T.READER, volleyError);
                if (resultListener!=null)
                    resultListener.onUpdateResult(ReaderActions.UpdateResult.FAILED);

            }
        };
        AppLog.d(T.READER, "updating comments");
        WordPress.getRestClientUtils().get(path, null, null, listener, errorListener);
    }
    private static void handleUpdateCommentsResponse(final JSONObject jsonObject, final long blogId, final ReaderActions.UpdateResultListener resultListener) {
        if (jsonObject==null) {
            if (resultListener!=null)
                resultListener.onUpdateResult(ReaderActions.UpdateResult.FAILED);
            return;
        }

        final Handler handler = new Handler();

        new Thread() {
            @Override
            public void run() {
                // request asks for only newer comments, so if it returns any comments then they are all new
                ReaderCommentList serverComments = ReaderCommentList.fromJson(jsonObject, blogId);
                final int numNew = serverComments.size();
                if (numNew > 0) {
                    AppLog.d(T.READER, "new comments found");
                    ReaderCommentTable.addOrUpdateComments(serverComments);
                }

                if (resultListener!=null) {
                    handler.post(new Runnable() {
                        public void run() {
                            resultListener.onUpdateResult(numNew > 0 ? ReaderActions.UpdateResult.CHANGED : ReaderActions.UpdateResult.UNCHANGED);
                        }
                    });
                }
            }
        }.start();
    }

    /*
     * used by post detail to generate a temporary "fake" comment id (see below)
     */
    public static long generateFakeCommentId() {
        return System.currentTimeMillis();
    }

    /*
     * add the passed comment text to the passed post - caller must pass a unique "fake" comment id
     * to give the comment that's generated locally
     */
    public static ReaderComment submitPostComment(final ReaderPost post,
                                                  final long fakeCommentId,
                                                  final String commentText,
                                                  final long replyToCommentId,
                                                  final ReaderActions.CommentActionListener actionListener) {
        if (post==null || TextUtils.isEmpty(commentText))
            return null;

        // create a "fake" comment that's added to the db so it can be shown right away - will be
        // replaced with actual comment if it succeeds to be posted, or deleted if comment fails
        // to be posted
        ReaderComment newComment = new ReaderComment();
        newComment.commentId = fakeCommentId;
        newComment.postId = post.postId;
        newComment.blogId = post.blogId;
        newComment.parentId = replyToCommentId;
        newComment.setText(commentText);
        String published = DateTimeUtils.nowUTC().toString();
        newComment.setPublished(published);
        newComment.timestamp = DateTimeUtils.iso8601ToTimestamp(published);
        ReaderUser currentUser = ReaderUserTable.getCurrentUser();
        if (currentUser!=null) {
            newComment.setAuthorAvatar(currentUser.getAvatarUrl());
            newComment.setAuthorName(currentUser.getDisplayName());
        }
        ReaderCommentTable.addOrUpdateComment(newComment);

        // different endpoint depending on whether the new comment is a reply to another comment
        final String path;
        if (replyToCommentId==0) {
            path = "sites/" + post.blogId + "/posts/" + post.postId + "/replies/new";
        } else {
            path = "sites/" + post.blogId + "/comments/" + Long.toString(replyToCommentId) + "/replies/new";
        }

        Map<String, String> params = new HashMap<String, String>();
        params.put("content", commentText);

        com.wordpress.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                ReaderCommentTable.deleteComment(post, fakeCommentId);
                AppLog.i(T.READER, "comment succeeded");
                ReaderComment newComment = ReaderComment.fromJson(jsonObject, post.blogId);
                ReaderCommentTable.addOrUpdateComment(newComment);
                if (actionListener!=null)
                    actionListener.onActionResult(true, newComment);
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                ReaderCommentTable.deleteComment(post, fakeCommentId);
                AppLog.w(T.READER, "comment failed");
                AppLog.e(T.READER, volleyError);
                if (actionListener!=null)
                    actionListener.onActionResult(false, null);
            }
        };

        AppLog.i(T.READER, "submitting comment");
        WordPress.getRestClientUtils().post(path, params, null, listener, errorListener);

        return newComment;
    }

    /*
     * like or unlike the passed comment
     */
    public static boolean performLikeAction(final ReaderComment comment,
                                            final boolean isAskingToLike) {
        if (comment == null) {
            return false;
        }

        // get this comment from db so we can revert on failure
        final ReaderComment originalComment = ReaderCommentTable.getComment(comment.blogId, comment.postId, comment.commentId);

        // nothing more to do if like status isn't changing
        if (originalComment != null && originalComment.isLikedByCurrentUser == isAskingToLike) {
            return true;
        }

        // update local db
        comment.isLikedByCurrentUser = isAskingToLike;
        if (isAskingToLike) {
            comment.numLikes++;
        } else if (!isAskingToLike && comment.numLikes > 0) {
            comment.numLikes--;
        }
        ReaderCommentTable.addOrUpdateComment(comment);
        ReaderLikeTable.setCurrentUserLikesComment(comment, isAskingToLike);

        // sites/$site/comments/$comment_ID/likes/new
        final String actionName = isAskingToLike ? "like" : "unlike";
        String path = "sites/" + comment.blogId + "/comments/" + comment.commentId + "/likes/";
        if (isAskingToLike) {
            path += "new";
        } else {
            path += "mine/delete";
        }

        com.wordpress.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                AppLog.d(T.READER, String.format("comment %s succeeded", actionName));
            }
        };

        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                String error = VolleyUtils.errStringFromVolleyError(volleyError);
                if (TextUtils.isEmpty(error)) {
                    AppLog.w(T.READER, String.format("comment %s failed", actionName));
                } else {
                    AppLog.w(T.READER, String.format("comment %s failed (%s)", actionName, error));
                }
                AppLog.e(T.READER, volleyError);
                // revert to original comment
                if (originalComment != null) {
                    ReaderCommentTable.addOrUpdateComment(originalComment);
                    ReaderLikeTable.setCurrentUserLikesComment(comment, originalComment.isLikedByCurrentUser);
                }
            }
        };

        WordPress.getRestClientUtils().post(path, listener, errorListener);

        return true;
    }
}
