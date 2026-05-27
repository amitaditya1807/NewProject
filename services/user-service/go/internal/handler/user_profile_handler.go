package handler

import (
	"net/http"
	"time"

	"user-service/internal/dto"
	"user-service/internal/model"
	"user-service/internal/service"

	"github.com/gin-gonic/gin"
)

type UserProfileHandler struct {
	service *service.UserProfileService
}

func NewUserProfileHandler(service *service.UserProfileService) *UserProfileHandler {
	return &UserProfileHandler{
		service: service,
	}
}

func (handler *UserProfileHandler) SaveProfile(ctx *gin.Context) {
	userID := ctx.GetString("userId")

	var request dto.SaveUserProfileRequest

	err := ctx.ShouldBindJSON(&request)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{
			"message": "invalid request body",
		})
		return
	}

	profile := &model.UserProfile{
		UserID:      userID,
		FullName:    request.FullName,
		PhoneNumber: request.PhoneNumber,
		AvatarURL:   request.AvatarURL,
		Bio:         request.Bio,
	}

	if request.DateOfBirth != "" {
		dateOfBirth, err := time.Parse("2006-01-02", request.DateOfBirth)
		if err != nil {
			ctx.JSON(http.StatusBadRequest, gin.H{
				"message": "dateOfBirth must be in yyyy-mm-dd format",
			})
			return
		}

		profile.DateOfBirth = &dateOfBirth
	}

	savedProfile, err := handler.service.SaveProfile(ctx.Request.Context(), profile)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{
			"message": err.Error(),
		})
		return
	}

	ctx.JSON(http.StatusOK, toUserProfileResponse(savedProfile))
}

func (handler *UserProfileHandler) GetProfile(ctx *gin.Context) {
	userID := ctx.GetString("userId")

	profile, err := handler.service.GetProfile(ctx.Request.Context(), userID)
	if err != nil {
		ctx.JSON(http.StatusNotFound, gin.H{
			"message": "profile not found",
		})
		return
	}

	ctx.JSON(http.StatusOK, toUserProfileResponse(profile))
}

func toUserProfileResponse(profile *model.UserProfile) dto.UserProfileResponse {
	dateOfBirth := ""

	if profile.DateOfBirth != nil {
		dateOfBirth = profile.DateOfBirth.Format("2006-01-02")
	}

	return dto.UserProfileResponse{
		ID:          profile.ID,
		UserID:      profile.UserID,
		FullName:    profile.FullName,
		PhoneNumber: profile.PhoneNumber,
		DateOfBirth: dateOfBirth,
		AvatarURL:   profile.AvatarURL,
		Bio:         profile.Bio,
		CreatedAt:   profile.CreatedAt.Format(time.RFC3339),
		UpdatedAt:   profile.UpdatedAt.Format(time.RFC3339),
	}
}
