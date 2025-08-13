# ConnectHub - Railway Deployment Guide

## 🚀 Deploy to Railway

### Prerequisites
- GitHub account
- Railway account (connect with GitHub)

### Step 1: Create GitHub Repository
```bash
# Initialize git repository
git init
git add .
git commit -m "Initial commit: ConnectHub Enterprise Chat Platform"

# Create repository on GitHub and push
git remote add origin https://github.com/YOUR_USERNAME/ConnectHub.git
git branch -M main
git push -u origin main
```

### Step 2: Deploy on Railway
1. Visit [railway.app](https://railway.app)
2. Click "New Project"
3. Choose "Deploy from GitHub repo"
4. Select your ConnectHub repository
5. Railway will automatically detect the Dockerfile

### Step 3: Add PostgreSQL Database
1. In your Railway project dashboard
2. Click "New Service" → "Database" → "PostgreSQL"
3. Railway will automatically provide `DATABASE_URL` environment variable

### Step 4: Environment Variables
Railway automatically sets:
- `DATABASE_URL` - PostgreSQL connection string
- `PORT` - Application port

No manual configuration needed!

### Step 5: Database Setup
After deployment, connect to your PostgreSQL service and run:
```sql
-- Copy content from database/schema.sql
```

### Step 6: Monitor Deployment
- Check Railway dashboard for deployment status
- View logs for any issues
- Your app will be available at the provided Railway URL

## 🎯 Railway Optimizations Included

- ✅ **Dockerfile optimized** for Railway
- ✅ **railway.json configuration** for deployment settings
- ✅ **Environment variable support** for DATABASE_URL and PORT
- ✅ **Health checks** configured
- ✅ **UTF-8 encoding** for international characters
- ✅ **Proper file permissions** in container
- ✅ **Build script** for verification

## 🔧 Technical Details

### Files Required for Railway:
- `Dockerfile` - Container configuration
- `railway.json` - Railway deployment settings
- `postgresql-42.7.7.jar` - Database driver
- All Java source files
- Configuration and schema files

### Automatic Features:
- Database connection via `DATABASE_URL`
- Dynamic port binding via `PORT` environment variable
- Health monitoring and restart policies
- Automatic HTTPS with custom domain support

Your ConnectHub is fully optimized for Railway deployment! 🎉
