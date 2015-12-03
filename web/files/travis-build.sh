HOME=$(dirname $0)
cd $HOME/..

npm install
npm test && npm run lint

