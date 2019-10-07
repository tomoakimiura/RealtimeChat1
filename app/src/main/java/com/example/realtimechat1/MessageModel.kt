package com.example.realtimechat1

class MessageModel() {
    var userName: String = ""
    var userPhotoUrl: String = ""
    var postedMessage: String = ""
    var postedImageUrl:String = ""

    constructor(userName: String,
                userPhotoUrl: String,
                postedMessage: String,
                postedImageUrl:String): this() {
        this.userName = userName
        this.userPhotoUrl = userPhotoUrl
        this.postedMessage = postedMessage
        this.postedImageUrl = postedImageUrl
    }
}