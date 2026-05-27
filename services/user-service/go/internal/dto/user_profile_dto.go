package dto

type SaveUserProfileRequest struct {
	FullName    string `json:"fullName"`
	PhoneNumber string `json:"phoneNumber"`
	DateOfBirth string `json:"dateOfBirth"`
	AvatarURL   string `json:"avatarUrl"`
	Bio         string `json:"bio"`
}

type UserProfileResponse struct {
	ID          string `json:"id"`
	UserID      string `json:"userId"`
	FullName    string `json:"fullName"`
	PhoneNumber string `json:"phoneNumber"`
	DateOfBirth string `json:"dateOfBirth"`
	AvatarURL   string `json:"avatarUrl"`
	Bio         string `json:"bio"`
	CreatedAt   string `json:"createdAt"`
	UpdatedAt   string `json:"updatedAt"`
}
