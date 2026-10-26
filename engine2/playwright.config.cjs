const {defineConfig}=require('@playwright/test');
module.exports=defineConfig({testDir:'.',testMatch:'browser.spec.cjs',timeout:20000,workers:2,retries:0,reporter:[['list'],['json',{outputFile:'results/browser-results.json'}]],use:{browserName:'chromium',viewport:{width:390,height:844},timezoneId:'Asia/Manila'}});
