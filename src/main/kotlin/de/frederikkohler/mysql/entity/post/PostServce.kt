package de.frederikkohler.mysql.entity.post

import de.frederikkohler.model.post.*
import de.frederikkohler.model.user.Users
import de.frederikkohler.plugins.dbQuery
import de.frederikkohler.service.DateTimeConverter
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class PostDetails(
    val post: Post,
    val images: List<String>,
    val comments: List<Comment>
)

@Serializable
data class Comment(
    val userId: Int,
    val content: String,
    val timestamp: String
)

interface PostService {
    suspend fun addPost(userID: Int, post: Post): Post?
    suspend fun addImage(postId: Int, imageURL: String): PostImage?
    suspend fun like(postID: Int, userID: Int): PostLike?
    suspend fun unLike(postID: Int, userID: Int): PostLike?
    suspend fun addStar(postID: Int, userID: Int): PostStar?
    suspend fun addComment(postID: Int, userID: Int, comment: String): PostComment?
    suspend fun findPostByID(postID: Int): Post?
    suspend fun deletePost(postID: Int): Boolean
    suspend fun getPosts(count: Int): List<Post>
}

class PostServiceDataService : PostService {

    override suspend fun addPost(userID: Int, post: Post): Post? = dbQuery{
        val insertStatement = Posts.insert {
            it[id] = post.id
            it[userId] = userID
            it[description] = post.description
            it[likes] = post.likesCount
            it[stars] = post.starsCount
            it[createdAt] = LocalDateTime.now()
            it[editAt] = null
        }
        insertStatement.resultedValues?.singleOrNull()?.let { row ->
            val postId = row[Posts.id]
            val username = Users.select { Users.id eq userID }.single()[Users.username]
            Post(
                id = postId,
                username = username,
                description = row[Posts.description],
                images = emptyList(),
                likesCount = row[Posts.likes],
                starsCount = row[Posts.stars],
                createdAt = row[Posts.createdAt].toString(),
                editAt = row[Posts.editAt]?.toString()
            )
        }
    }

    override suspend fun addImage(postId: Int, imageURL: String): PostImage? = dbQuery {
        val insertStatement = PostImages.insert {
            it[this.postId] = postId
            it[this.imageUrl] = imageURL
        }
        insertStatement.resultedValues?.singleOrNull()?.let { row ->
            PostImage(
                postId = row[PostImages.postId],
                imageUrl = row[PostImages.imageUrl]
            )
        }
    }

    override suspend fun addStar(postID: Int, userID: Int): PostStar? = dbQuery {
        val insertStatement = PostStars.insert {
            it[postId] = postID
            it[userId] = userID
        }
        insertStatement.resultedValues?.singleOrNull()?.let { row ->
            PostStar(
                postId = row[PostStars.postId],
                userId = row[PostStars.userId],
            )
        }
    }

    override suspend fun deletePost(postID: Int): Boolean = dbQuery {
        Posts.deleteWhere { id eq postID }>0
    }

    override suspend fun addComment(postID: Int, userID: Int, comment: String): PostComment? = dbQuery {
        val insertStatement = PostComments.insert {
            it[postId] = postID
            it[userId] = userID
            it[PostComments.comment] = comment
            it[timestamp] = LocalDate.now().toString()
        }
        insertStatement.resultedValues?.singleOrNull()?.let { row ->
            PostComment(
                postId = row[PostComments.postId],
                userId = row[PostComments.userId],
                comment = row[PostComments.comment],
                timestamp = row[PostComments.timestamp]
            )
        }
    }

    override suspend fun findPostByID(postID: Int): Post? = dbQuery {
        Posts.innerJoin(Users)
            .slice(Posts.id, Users.username, Posts.description, Posts.likes, Posts.stars)
            .select {(Posts.id eq postID) }
            .map { it ->
                val postId = it[Posts.id]
                val likesCount = PostLikes.select { PostLikes.postId eq postId }.count()
                val starsCount = PostStars.select { PostStars.postId eq postId }.count()
                val images = PostImages.select { PostImages.postId eq postId }
                    .map { post -> post[PostImages.imageUrl] }

                Post(
                    id = postId,
                    username = it[Users.username],
                    description = it[Posts.description],
                    images = images,
                    likesCount = likesCount.toInt(),
                    starsCount = starsCount.toInt(),
                    createdAt = it[Posts.createdAt].toString(),
                    editAt = it[Posts.editAt].toString()
                )
            }
            .singleOrNull()
    }

    override suspend fun getPosts(count: Int): List<Post> = dbQuery {
        Posts.innerJoin(Users)
            .slice(
                Posts.id,
                Users.username,
                Posts.description,
                Posts.likes,
                Posts.stars,
                Posts.createdAt,
                Posts.editAt // Sicherstellen, dass `editAt` enthalten ist
            )
            .selectAll()
            .limit(count)
            .map { row ->
                val postId = row[Posts.id]
                val likesCount = PostLikes.selectAll().where { PostLikes.postId eq postId }.count()
                val starsCount = PostStars.selectAll().where { PostStars.postId eq postId }.count()
                val images = PostImages.selectAll().where { PostImages.postId eq postId }
                    .map { it[PostImages.imageUrl] }
                Post(
                    id = postId,
                    username = row[Users.username],
                    description = row[Posts.description],
                    images = images,
                    likesCount = likesCount.toInt(),
                    starsCount = starsCount.toInt(),
                    createdAt = row[Posts.createdAt].toString(),
                    editAt = row[Posts.editAt]?.toString() // Umgang mit nullable Feld
                )
            }
    }

    override suspend fun like(postID: Int, userID: Int): PostLike? = dbQuery {
        val insertStatement = PostLikes.insert {
            it[postId] = postID
            it[userId] = userID
        }

        insertStatement.resultedValues?.singleOrNull()?.let { row ->
            PostLike(
                postId = row[PostLikes.postId],
                userId = row[PostLikes.userId]
            )
        }
    }

    override suspend fun unLike(postID: Int, userID: Int): PostLike? = dbQuery {
        // Finden des zu löschenden Eintrags
        val likeToDelete = PostLikes.select { (PostLikes.postId eq postID) and  (PostLikes.userId eq userID) }
            .singleOrNull()
            ?.let { row ->
                PostLike(
                    postId = row[PostLikes.postId],
                    userId = row[PostLikes.userId]
                )
            }

        // Wenn der Eintrag gefunden wurde, löschen
        if (likeToDelete != null) {
            PostLikes.deleteWhere { (PostLikes.postId eq postID) and (PostLikes.userId eq userID) }
        }

        likeToDelete
    }
}

