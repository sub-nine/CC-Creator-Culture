package com.sub9.userservice.notification.domain.model;

public enum SlackDeliveryStatus {
    PENDING,  //Slack 발송대기중
    SENDING,  //Slack 발송중
    SENT,     //Slack 발송성공
    FAILED    //Slack 발송실패
}