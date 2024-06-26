package de.frederikkohler.model.user

import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

data class UserProfile(
    val userId:Int=0,
    val firstname: String,
    val lastname: String,
    val email: String,
    val bio: String?
)

object UserProfiles: Table(){
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val firstname= varchar("firstname",255)
    val lastname= varchar("lastname",255)
    val email= varchar("email",255)
    val bio = varchar("bio", 255).nullable()

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(UserPasswords.userId)
}
