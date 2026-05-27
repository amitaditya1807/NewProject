package repository

import (
	"context"
	"fmt"

	"user-service/internal/model"

	"github.com/jackc/pgx/v5/pgxpool"
)

type UserProfileRepository struct {
	db *pgxpool.Pool
}

func NewUserProfileRepository(db *pgxpool.Pool) *UserProfileRepository {
	return &UserProfileRepository{
		db: db,
	}
}

func (repository *UserProfileRepository) GetByUserID(ctx context.Context, userID string) (*model.UserProfile, error) {
	query := `
		SELECT id, user_id, full_name, phone_number, date_of_birth, avatar_url, bio, created_at, updated_at
		FROM user_profiles
		WHERE user_id = $1
	`

	profile := &model.UserProfile{}

	err := repository.db.QueryRow(ctx, query, userID).Scan(
		&profile.ID,
		&profile.UserID,
		&profile.FullName,
		&profile.PhoneNumber,
		&profile.DateOfBirth,
		&profile.AvatarURL,
		&profile.Bio,
		&profile.CreatedAt,
		&profile.UpdatedAt,
	)

	if err != nil {
		return nil, fmt.Errorf("failed to get user profile: %w", err)
	}

	return profile, nil
}

func (repository *UserProfileRepository) Save(ctx context.Context, profile *model.UserProfile) (*model.UserProfile, error) {
	query := `
		INSERT INTO user_profiles (user_id, full_name, phone_number, date_of_birth, avatar_url, bio)
		VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (user_id)
		DO UPDATE SET
			full_name = EXCLUDED.full_name,
			phone_number = EXCLUDED.phone_number,
			date_of_birth = EXCLUDED.date_of_birth,
			avatar_url = EXCLUDED.avatar_url,
			bio = EXCLUDED.bio,
			updated_at = CURRENT_TIMESTAMP
		RETURNING id, user_id, full_name, phone_number, date_of_birth, avatar_url, bio, created_at, updated_at
	`

	savedProfile := &model.UserProfile{}

	err := repository.db.QueryRow(
		ctx,
		query,
		profile.UserID,
		profile.FullName,
		profile.PhoneNumber,
		profile.DateOfBirth,
		profile.AvatarURL,
		profile.Bio,
	).Scan(
		&savedProfile.ID,
		&savedProfile.UserID,
		&savedProfile.FullName,
		&savedProfile.PhoneNumber,
		&savedProfile.DateOfBirth,
		&savedProfile.AvatarURL,
		&savedProfile.Bio,
		&savedProfile.CreatedAt,
		&savedProfile.UpdatedAt,
	)

	if err != nil {
		return nil, fmt.Errorf("failed to save user profile: %w", err)
	}

	return savedProfile, nil
}
