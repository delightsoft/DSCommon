gulp = require('gulp')
coffee = require('gulp-coffee')
compass = require('gulp-compass')
minifyCSSGulp = require('gulp-minify-css')
concat = require('gulp-concat')
uglify = require('gulp-uglify')
header = require('gulp-header')
rename = require('gulp-rename')
wrap = require('gulp-wrap-umd')

gutil = require('gulp-util')

require('./build.coffee') '.', {gulp, coffee, compass, minifyCSSGulp, concat, uglify, header, rename, wrap, gutil}
