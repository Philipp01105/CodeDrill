# CodeDrill

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen?style=for-the-badge&logo=spring-boot" alt="Spring Boot 3.4.4"/>
  <img src="https://img.shields.io/badge/Tailwind%20CSS-3.3.3-blue?style=for-the-badge&logo=tailwindcss" alt="Tailwind CSS 3.3.3"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="MIT License"/>
</p>

## 📋 Overview

CodeDrill is an interactive learning platform designed to help students learn and practice AP Computer Science A concepts through interactive programming tasks. The platform allows users to solve Java programming problems, submit their solutions, and receive immediate feedback.

## ✨ Features

- **🔐 User Management**: Authentication, authorization, and user administration
- **📚 Task Management**: Creation, editing, and organization of programming tasks
- **📝 Submissions & Evaluations**: Automatic code evaluation and detailed feedback
- **📊 Learning Analytics**: Progress tracking and performance analysis
- **🌓 Dark/Light Mode**: User-friendly interface with support for light and dark themes
- **🔄 Real-time Updates**: Immediate feedback on code submissions

## 🛠️ Technologies

### Backend
- **Java 17**
- **Spring Boot 3.4.4** (with Spring Security, Spring Data JPA)
- **PostgreSQL** (production environment)
- **H2 Database** (development environment)
- **Docker** for isolated code execution

### Frontend
- **Thymeleaf**
- **TailwindCSS 3.3.3**
- **PostCSS 8.4.27**
- **Autoprefixer 10.4.14**

## 🚀 Installation and Setup

### Prerequisites
- Java 17 or higher
- Maven
- Node.js and NPM
- PostgreSQL (for production environment)
- Docker (for isolated code execution)

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/username/CodeDrill.git
   cd CodeDrill
   ```

2. Install Maven dependencies:
   ```bash
   ./mvnw install
   ```

3. Install NPM packages:
   ```bash
   npm install
   ```

4. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```

5. Access the application at:
   ```
   http://localhost:8080
   ```

## 🧪 Development

The application uses Maven for Java dependencies and NPM for frontend components. TailwindCSS is used for styling. For local development, you can use the H2 database, while PostgreSQL is recommended for production.

## 🐳 Docker

The project contains a `Dockerfile.coderunner` for containerizing the code execution environment to ensure security and isolation when executing user code.

## 📄 License

This project is licensed under the MIT License. See [LICENSE](license.md) for more details.
