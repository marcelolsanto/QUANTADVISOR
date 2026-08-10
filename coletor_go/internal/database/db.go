package database

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"

	"github.com/golang-migrate/migrate/v4"
	"github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	_ "github.com/lib/pq"
	"github.com/redis/go-redis/v9"
)

// Rdb é a variável global que mantém o pool de conexões com o Redis ativo
var Rdb *redis.Client
var Ctx = context.Background()

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func ConectarRedis() {
	redisHost := getEnvOrDefault("REDIS_HOST", "quant_redis")
	redisPort := getEnvOrDefault("REDIS_PORT", "6379")
	redisPass := os.Getenv("REDIS_PASSWORD")

	Rdb = redis.NewClient(&redis.Options{
		Addr:     fmt.Sprintf("%s:%s", redisHost, redisPort),
		Password: redisPass,
		DB:       0,
	})

	// Testa a conexão enviando um PING
	_, err := Rdb.Ping(Ctx).Result()
	if err != nil {
		log.Fatalf("❌ Erro fatal ao conectar no Redis via Go: %v", err)
	}

	log.Println("⚡ Conexão com o Redis estabelecida com sucesso pelo Maestro Go.")
}

// Conn é a variável global que mantém o pool de conexões ativo
var Conn *sql.DB

func Conectar() {
	var err error
	connStr := os.Getenv("DATABASE_URL")
	if connStr == "" {
		dbHost := getEnvOrDefault("DB_HOST", "quantadvisor_pg")
		dbPort := getEnvOrDefault("DB_PORT", "5432")
		dbUser := getEnvOrDefault("DB_USER", "devuser")
		dbPass := getEnvOrDefault("DB_PASSWORD", "devpassword")
		dbName := getEnvOrDefault("DB_NAME", "devdb")
		connStr = fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable", dbHost, dbPort, dbUser, dbPass, dbName)
	}

	Conn, err = sql.Open("postgres", connStr)
	
	if err != nil {
		log.Fatalf("❌ Erro fatal ao montar a string de conexão: %v", err)
	}

	// Configura limites do pool de conexões para prevenir saturação no PostgreSQL
	Conn.SetMaxOpenConns(50)
	Conn.SetMaxIdleConns(10)

	// Testa se o banco realmente está respondendo
	if err = Conn.Ping(); err != nil {
		log.Fatalf("❌ Banco de dados inatingível: %v", err)
	}

	log.Println("🗄️ Conexão com o PostgreSQL estabelecida com sucesso (Pool Limit: 50 conexões).")
	ExecutarMigracoes()
}

func ExecutarMigracoes() {
	driver, err := postgres.WithInstance(Conn, &postgres.Config{})
	if err != nil {
		log.Printf("⚠️ [MIGRATE] Falha ao instanciar driver Postgres: %v", err)
		return
	}

	m, err := migrate.NewWithDatabaseInstance("file://migrations", "postgres", driver)
	if err != nil {
		m, err = migrate.NewWithDatabaseInstance("file://coletor_go/migrations", "postgres", driver)
	}

	if err != nil {
		log.Printf("⚠️ [MIGRATE] Falha ao carregar migrações SQL: %v", err)
		return
	}

	if err := m.Up(); err != nil && err != migrate.ErrNoChange {
		log.Printf("⚠️ [MIGRATE] Erro ao aplicar migrações: %v", err)
	} else {
		log.Println("✅ [MIGRATE] Migrações de banco de dados executadas com sucesso via golang-migrate!")
	}
}

// Auxiliar global para verificar linhas afetadas em transações
func RowsAffected(res sql.Result) int64 {
	rows, _ := res.RowsAffected()
	return rows
}