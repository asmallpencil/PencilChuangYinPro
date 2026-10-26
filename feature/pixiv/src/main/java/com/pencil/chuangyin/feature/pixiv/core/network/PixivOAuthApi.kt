package com.pencil.chuangyin.feature.pixiv.core.network

import com.pencil.chuangyin.feature.pixiv.data.dto.OAuthTokenResponseDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/** oauth.secure.pixiv.net 的授权码 / 刷新令牌接口。 */
interface PixivOAuthApi {

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun postAuthToken(
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("client_id") clientId: String = PixivConfig.CLIENT_ID,
        @Field("client_secret") clientSecret: String = PixivConfig.CLIENT_SECRET,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String = PixivConfig.REDIRECT_URI,
        @Field("include_policy") includePolicy: Boolean = true,
    ): OAuthTokenResponseDto

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun postRefreshAuthToken(
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String = PixivConfig.CLIENT_ID,
        @Field("client_secret") clientSecret: String = PixivConfig.CLIENT_SECRET,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("include_policy") includePolicy: Boolean = true,
    ): OAuthTokenResponseDto
}
