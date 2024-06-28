import os
import time
import sqlite3
import urllib.parse
import json
import requests
from ratelimiter import RateLimiter
import undetected_chromedriver as uc
from selenium.webdriver.common.by import By

def fetch_true_leetcode_handles(db_name):
    true_leetcode = []

    try:
        # Establish database connection
        conn = sqlite3.connect(db_name)
        cursor = conn.cursor()

        # Execute SQL query to fetch true leetcode handles
        cursor.execute("SELECT handle, leetcode_handle FROM users_data WHERE leetcode_url_exists = 1")
        rows = cursor.fetchall()

        # Iterate over the result set and add true leetcode handles to the list
        for row in rows:
            handle, leetcode_handle = row
            if leetcode_handle is not None:
                true_leetcode.append((handle, leetcode_handle))

    except sqlite3.Error as e:
        print("Error fetching true Leetcode handles:", e)
    finally:
        if conn:
            conn.close()

    return true_leetcode

def scrape_leetcode(true_leetcode):
    print("Leetcode scraping in progress...")

    # Create or clear the file for writing
    with open("leetcode_ratings.txt", "w") as file:
        file.write("")

    counter = 1
    size = len(true_leetcode)

    # Rate limit the function to a maximum of 2 requests per second
    limiter = RateLimiter(max_calls=MAX_REQUESTS_PER_SECOND, period=1)

    # Create chrome options
    options = uc.ChromeOptions()
    options.add_argument("--auto-open-devtools-for-tabs")

    # Configure undetected-chromedriver to run in headless mode
    driver = uc.Chrome(version_main=126, options=options)

    driver.get("https://github.com/login")
    time.sleep(5)
    # find element by name login
    login = driver.find_element(By.NAME, "login")
    # find element by name password
    password = driver.find_element(By.NAME, "password")
    # find element by id signin_btn
    signin_btn = driver.find_element(By.NAME, "commit")
    # load username from environment variable
    username = os.environ.get("USERNAME")
    # load password from environment variable
    passwd = os.environ.get("PASSWD")
    # send username to login
    login.send_keys(username)
    # send password to password
    password.send_keys(passwd)
    # click on signin_btn
    signin_btn.click()

    # Open new tab to https://leetcode.com/accounts/login/
    driver.get('https://leetcode.com/accounts/github/login/?next=%2F')
    time.sleep(5)
    try:
        # Clock button with name authorize
        authorize_btn = driver.find_element(By.NAME, "authorize")
        # Click on authorize button
        authorize_btn.click()
        time.sleep(5)
    except Exception as e:
        print(f"Error: {e}")

    for handle, leetcode_handle in true_leetcode:
        # Construct URL for API request
        encoded_leetcode_handle = urllib.parse.quote(leetcode_handle, safe='')
        url = LEETCODE_URL.replace("{<username>}", encoded_leetcode_handle)
        url = url.replace(" ", "%20")
        try:
            with limiter:
                headers = None
                print("URL:", url)
                driver.get(url)

                # Parse JSON response
                json_content = driver.find_element(By.TAG_NAME, "pre").text

                # convert JSON CONTENT TO JSON PARSEABLE OBJECT
                json_content = json.loads(json_content)

                try:
                    # Get rating from JSON response
                    rating = json_content['data']['userContestRanking']['rating']
                except TypeError:
                    # Handle NoneType error
                    print(f"Rating for {handle} with leetcode handle {leetcode_handle} not found.")
                    rating = 0

                rating = round(float(rating))
                # Print rating information
                print(f"({counter}/{size}) Leetcode rating for {handle} with leetcode handle {leetcode_handle} is: {rating}")

                # Write to text file
                with open("leetcode_ratings.txt", "a") as file:
                    file.write(f"{handle},{leetcode_handle},{rating}\n")

                counter += 1

        except Exception as e:
            # Error handling
            raise RuntimeError(f"Error fetching leetcode rating for {handle} with leetcode handle {leetcode_handle}: {e}")

    print("Leetcode scraping completed.")

# Constants
LEETCODE_URL = '''
https://leetcode.com/graphql?query=query{userContestRanking(username:"{<username>}"){rating}}
'''
MAX_REQUESTS_PER_SECOND = 2

# Main function
def main():
    true_leetcode_handles = fetch_true_leetcode_handles("cmrit")
    scrape_leetcode(true_leetcode_handles)

if __name__ == "__main__":
    main()
