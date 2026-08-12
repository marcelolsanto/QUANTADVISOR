package middleware

import (
	"context"
	"net/http"
	"os"
	"strings"

	"github.com/dgrijalva/jwt-go"
)

func GetJWTSecretKey() []byte {
	secret := os.Getenv("JWT_SECRET")
	if secret == "" {
		secret = "quantadvisor_master_key_2026"
	}
	return []byte(secret)
}

var JwtSecretKey = GetJWTSecretKey()

type Claims struct {
	UsuarioID int    `json:"usuario_id"`
	Role      string `json:"role"`
	jwt.StandardClaims
}

// ProtegerRota atua APENAS na verificação do Crachá (Token JWT)
func ProtegerRota(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		
		// 🤖 AUTENTICAÇÃO DE ROBÔS INTERNOS
		// Exige INTERNAL_BOT_SECRET configurado no ambiente para validar comunicação entre containers
		botSecret := os.Getenv("INTERNAL_BOT_SECRET")
		if botSecret != "" && r.Header.Get("X-Internal-Bot") == botSecret {
			ctx := context.WithValue(r.Context(), "usuario_id", 1)
			ctx = context.WithValue(ctx, "role", "GESTOR")
			r = r.WithContext(ctx)
			next.ServeHTTP(w, r)
			return
		}

		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			http.Error(w, `{"sucesso": false, "erro": "Acesso negado. Token nao fornecido."}`, http.StatusUnauthorized)
			return
		}

		tokenString := strings.Replace(authHeader, "Bearer ", "", 1)
		claims := &Claims{}

		token, err := jwt.ParseWithClaims(tokenString, claims, func(token *jwt.Token) (interface{}, error) {
			return GetJWTSecretKey(), nil
		})

		if err != nil || !token.Valid {
			http.Error(w, `{"sucesso": false, "erro": "Sessao expirada ou token invalido."}`, http.StatusUnauthorized)
			return
		}

		ctx := context.WithValue(r.Context(), "usuario_id", claims.UsuarioID)
		ctx = context.WithValue(ctx, "role", claims.Role)
		r = r.WithContext(ctx)

		next.ServeHTTP(w, r)
	}
}

func CORSMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
		w.Header().Set("Access-Control-Allow-Headers", "Accept, Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, Bypass-Tunnel-Reminder, bypass-tunnel-reminder, X-Internal-Bot")
		w.Header().Set("Content-Type", "application/json")

		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		next.ServeHTTP(w, r)
	})
}