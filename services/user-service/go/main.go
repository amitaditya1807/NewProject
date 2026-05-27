package main

import (
	"context"
	"log"
	"net/http"

	"user-service/internal/config"
	"user-service/internal/db"
	"user-service/internal/handler"
	"user-service/internal/middleware"
	"user-service/internal/repository"
	"user-service/internal/service"

	"github.com/gin-gonic/gin"
)

func main() {
	ctx := context.Background()

	cfg := config.Load()

	database, err := db.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatal(err)
	}
	defer database.Close()

	err = db.RunMigrations(ctx, database)
	if err != nil {
		log.Fatal(err)
	}

	userProfileRepository := repository.NewUserProfileRepository(database)
	userProfileService := service.NewUserProfileService(userProfileRepository)
	userProfileHandler := handler.NewUserProfileHandler(userProfileService)

	router := gin.Default()

	router.GET("/health", func(ctx *gin.Context) {
		ctx.JSON(http.StatusOK, gin.H{
			"service": "user-service",
			"status":  "UP",
		})
	})

	profileRoutes := router.Group("/api/users")
	profileRoutes.Use(middleware.AuthMiddleware(cfg.JWTSecret))

	profileRoutes.GET("/profile", userProfileHandler.GetProfile)
	profileRoutes.POST("/profile", userProfileHandler.SaveProfile)

	err = router.Run(":" + cfg.Port)
	if err != nil {
		log.Fatal(err)
	}
}