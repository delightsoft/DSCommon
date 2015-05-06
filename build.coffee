#
# It's common build code that is used from local gulpfile.coffee and from gulpfile.coffee of a final application
#

module.exports = (path, {gulp, coffee, compass, concat, uglify, header, rename, wrap, gutil}) ->

  pkg = require('./package.json')
  banner = "/*! #{ pkg.name } #{ pkg.version } */\n"

  gulp.task 'default', ['dsc-build'], ->

  gulp.task 'dsc-build', ['dsc-uglify'] , ->
    gulp.watch "#{path}/app/ngapp/**/*.coffee", ['dsc-uglify']

  gulp.task 'dsc-coffee', ->
    gulp.src("#{path}/app/ngApp/**/*.coffee")
    .pipe(coffee())
      .on('error', gutil.log)
      .on('error', gutil.beep)
    .pipe(gulp.dest("#{path}/static/ngApp/js/coffee"))

  gulp.task 'dsc-concat', ['dsc-coffee'], ->
    gulp.src("#{path}/static/ngApp/js/coffee/**/*.js")
    .pipe(concat('dscommon.js'))
    .pipe(header(banner))
    .pipe(gulp.dest("#{path}/static/ngApp/js"))

  gulp.task 'dsc-uglify', ['dsc-concat'], ->
    gulp.src("#{path}/static/ngApp/js/dscommon.js")
    .pipe(uglify())
    .pipe(header(banner))
    .pipe(rename('dscommon.min.js'))
    .pipe(gulp.dest("#{path}/static/ngApp/js"))
