Local deployment
################

Make sure config.js has:
DATA_BASE_URL: "http://localhost:8080",

Run in the folder whete root.json is:
npx http-server -p 8080 --cors

Then run in folder frontend:
npm run dev


Web deployment
##############
1) Zip the folder with root.json and all its subfolders
2) Drop it to the project czech-castles-gallery on Netlify
3) Go to frontend folder, run
   npm install
   npm run build
4) Zip created dist folder and drop it to czech-castles on Netlify

