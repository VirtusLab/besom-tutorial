package besom.examples.lambda

import scala.util.Random

def renderFeed(event: APIGatewayProxyEvent): APIGatewayProxyResponse =
  val entries: Vector[CatPost] = dynamodb.getEntries(Env.region)

  val entriesHtml = entries
    .map { entry =>
      val randomAvatar = math.abs(entry.userName.hashCode()) % 70 + 1

      s"""
    <li role="article" class="relative pl-6 ">
      <div class="flex flex-col flex-1 gap-2">
        <a href="#" class="absolute z-10 inline-flex items-center justify-center w-6 h-6 text-white rounded-full -left-3 ring-2 ring-white">
          <img src="https://i.pravatar.cc/48?img=$randomAvatar" alt="${entry.userName}" title="${entry.userName}" width="48" height="48" class="max-w-full rounded-full" />
        </a>
        <h4 class="flex flex-col items-start text-base font-medium leading-6 lg:items-center md:flex-row text-slate-700">
          <span class="flex-1">${entry.userName}<span class="text-sm font-normal text-slate-500"> commented</span></span>
          <span class="text-xs font-normal text-slate-400"> ${entry.timestamp}</span>
        </h4>
        <img src="${entry.catPictureURL}" alt="Cat picture" class="flex-1 object-cover w-full h-64 rounded-lg" />
        <p class="text-sm text-slate-500">${entry.comment}</p>
      </div>
    </li>
    """
    }
    .mkString("\n")

  val html = s"""
  <!DOCTYPE html>
  <html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CatPost</title>
    <script src="https://cdn.tailwindcss.com"></script>
  </head>
  <body class="bg-gray-100 flex items-center justify-center">
    <div class="relative flex flex-col w-1/3">
      <h1 class="mb-12 text-5xl text-center text-slate-700 pt-6">CatPost!</h1>
      <div class="relative flex flex-col pt-6">
        <form enctype="multipart/form-data" action="/${Env.stage}/post" method="POST">
          <label class="block mb-2 text-sm font-medium text-slate-400" for="name">Name</label>
          <input id="name" type="text" name="name" placeholder="Your name" class="relative w-full px-4 py-2 text-sm placeholder-transparent transition-all border rounded outline-none focus-visible:outline-none peer border-slate-200 text-slate-500 autofill:bg-white invalid:border-pink-500 invalid:text-pink-500 focus:border-emerald-500 focus:outline-none invalid:focus:border-pink-500 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400">
          <label for="comment" class="block mb-2 text-sm font-medium text-slate-400">Write your comment</label>
          <textarea id="comment" type="text" name="comment" placeholder="Write your comment" rows="3" class="relative w-full px-4 py-2 text-sm placeholder-transparent transition-all border rounded outline-none focus-visible:outline-none peer border-slate-200 text-slate-500 autofill:bg-white invalid:border-pink-500 invalid:text-pink-500 focus:border-emerald-500 focus:outline-none invalid:focus:border-pink-500 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400"></textarea>
          <label class="block mb-2 text-sm font-medium text-slate-400" for="file_input">Add a photo of your cat!</label>
          <input class="block w-full text-sm text-slate-400 border border-slate-200 rounded-lg cursor-pointer focus:outline-none" aria-describedby="file_input_help" id="file_input" type="file" name="picture">
          <p class="mt-1 text-sm text-slate-500" id="file_input_help">PNG, JPG or GIF.</p>
          <button type="submit" class="inline-flex items-center justify-center h-12 gap-2 px-6 text-sm font-medium tracking-wide text-white transition duration-300 rounded focus-visible:outline-none whitespace-nowrap bg-emerald-500 hover:bg-emerald-600 focus:bg-emerald-700 disabled:cursor-not-allowed disabled:border-emerald-300 disabled:bg-emerald-300 disabled:shadow-none">
            <span>Post!</span>
          </button>
        </form>
      </div>
      <ul aria-label="Cats" role="feed" class="relative flex flex-col gap-12 py-12 pl-6 before:absolute before:top-0 before:left-6 before:h-full before:border before:-translate-x-1/2 before:border-slate-200 before:border-dashed after:absolute after:top-6 after:left-6 after:bottom-6 after:border after:-translate-x-1/2 after:border-slate-200 ">
        $entriesHtml
      </ul>
    </div>
  </body>
  </html>
  """

  APIGatewayProxyResponse(
    statusCode = 200,
    headers = Map("Content-Type" -> "text/html"),
    body = html
  )

@main def renderFeedMain: Unit =
  lambdaRuntime(renderFeed)
