# collect_repos.py - SAFE VERSION USING .env
import os
import requests
import subprocess
import time
from dotenv import load_dotenv

# Load token from .env
load_dotenv()
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
if not GITHUB_TOKEN:
    raise ValueError("Please set GITHUB_TOKEN in .env file")

HEADERS = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json"
}

BASE_DIR = "../repos"
os.makedirs(BASE_DIR, exist_ok=True)
TARGET_COUNT = 120

QUERIES = [
    "spring boot", "spring-boot", "spring boot rest api",
    "spring boot microservice", "spring boot application", "spring-boot-starter-web"
]
YEARS = [f"{y}..{y+1}" for y in range(2018, 2026)]

def clone_repo(repo):
    name = repo["full_name"].replace("/", "_")
    path = os.path.join(BASE_DIR, name)
    if os.path.exists(path):
        return False
    print(f"Cloning → {repo['full_name']} | Stars: {repo['stargazers_count']}")
    try:
        subprocess.run(
            ["git", "clone", "--depth", "1", "--quiet", repo["clone_url"], path],
            check=True, timeout=60
        )
        return True
    except:
        print(f"Failed → {repo['full_name']}")
        return False

def main():
    collected = len([d for d in os.listdir(BASE_DIR) if os.path.isdir(os.path.join(BASE_DIR, d))])
    print(f"Already collected: {collected}/{TARGET_COUNT} repos")

    for query in QUERIES:
        if collected >= TARGET_COUNT:
            break
        for year in YEARS:
            if collected >= TARGET_COUNT:
                break
            for page in range(1, 7):
                if collected >= TARGET_COUNT:
                    break
                url = f"https://api.github.com/search/repositories"
                params = {
                    "q": f"{query} language:Java created:{year} fork:false",
                    "sort": "stars",
                    "order": "desc",
                    "per_page": 100,
                    "page": page
                }
                print(f"Searching: {query} | {year} | page {page}")
                try:
                    r = requests.get(url, headers=HEADERS, params=params, timeout=10)
                    if r.status_code == 403:
                        print("Rate limited! Waiting 60 seconds...")
                        time.sleep(60)
                        continue
                    data = r.json()
                    items = data.get("items", [])
                    if not items:
                        break
                    for repo in items:
                        if collected >= TARGET_COUNT:
                            break
                        if clone_repo(repo):
                            collected += 1
                            print(f"Success → Total: {collected}/{TARGET_COUNT}")
                    time.sleep(2)
                except Exception as e:
                    print(f"Error: {e}")
                    time.sleep(5)

    print("\nCOLLECTION COMPLETED!")
    print(f"Total repositories cloned: {collected}")

if __name__ == "__main__":
    main()