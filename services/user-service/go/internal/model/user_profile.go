package model

import "time"

type UserProfile struct {
	ID          string
	UserID      string
	FullName    string
	PhoneNumber string
	DateOfBirth *time.Time
	AvatarURL   string
	Bio         string
	CreatedAt   time.Time
	UpdatedAt   time.Time
}
