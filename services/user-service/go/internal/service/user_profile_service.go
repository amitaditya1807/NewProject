package service

import (
	"context"
	"fmt"
	"strings"

	"user-service/internal/model"
	"user-service/internal/repository"
)

type UserProfileService struct {
	repository *repository.UserProfileRepository
}

func NewUserProfileService(repository *repository.UserProfileRepository) *UserProfileService {
	return &UserProfileService{
		repository: repository,
	}
}

func (service *UserProfileService) GetProfile(ctx context.Context, userID string) (*model.UserProfile, error) {
	return service.repository.GetByUserID(ctx, userID)
}

func (service *UserProfileService) SaveProfile(ctx context.Context, profile *model.UserProfile) (*model.UserProfile, error) {
	profile.FullName = strings.TrimSpace(profile.FullName)

	if profile.UserID == "" {
		return nil, fmt.Errorf("user id is required")
	}

	if profile.FullName == "" {
		return nil, fmt.Errorf("full name is required")
	}

	return service.repository.Save(ctx, profile)
}
