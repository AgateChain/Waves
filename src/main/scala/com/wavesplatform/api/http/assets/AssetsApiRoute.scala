package com.wavesplatform.api.http.assets

import java.util.concurrent._

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.google.common.base.Charsets
import com.wavesplatform.account.Address
import com.wavesplatform.api.http._
import com.wavesplatform.http.BroadcastRoute
import com.wavesplatform.settings.RestAPISettings
import com.wavesplatform.state.{Blockchain, ByteStr}
import com.wavesplatform.transaction.assets.IssueTransaction
import com.wavesplatform.transaction.assets.exchange.Order
import com.wavesplatform.transaction.assets.exchange.OrderJson._
import com.wavesplatform.transaction.smart.script.ScriptCompiler
import com.wavesplatform.transaction.{AssetId, AssetIdStringLength, TransactionFactory, ValidationError}
import com.wavesplatform.utils.{Base58, Time, _}
import com.wavesplatform.utx.UtxPool
import com.wavesplatform.wallet.Wallet
import io.netty.channel.group.ChannelGroup
import io.swagger.annotations._
import javax.ws.rs.Path
import monix.eval.Task
import monix.execution.Scheduler
import play.api.libs.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}

@Path("/tokens")
@Api(value = "tokens")
case class AssetsApiRoute(settings: RestAPISettings, wallet: Wallet, utx: UtxPool, allChannels: ChannelGroup, blockchain: Blockchain, time: Time)
    extends ApiRoute
    with BroadcastRoute {

  private val distributionTaskScheduler = {
    val executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable](AssetsApiRoute.MAX_DISTRIBUTION_TASKS))
    Scheduler(executor)
  }

  override lazy val route =
<<<<<<< HEAD
    pathPrefix("tokens") {
      balance ~ balances ~ issue ~ reissue ~ burnRoute ~ transfer ~ massTransfer ~ signOrder ~ balanceDistribution ~ details ~ sponsorRoute
=======
    pathPrefix("assets") {
      balance ~ balances ~ issue ~ reissue ~ burnRoute ~ transfer ~ massTransfer ~ signOrder ~ balanceDistributionAtHeight ~ balanceDistribution ~ details ~ sponsorRoute
>>>>>>> 501f3836ad1f1aadb0f0a7ee82c490cb3425da1f
    }

  @Path("/balance/{address}/{assetId}")
  @ApiOperation(value = "token's balance", notes = "Account's balance by given token", httpMethod = "GET")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "address", value = "Address", required = true, dataType = "string", paramType = "path"),
      new ApiImplicitParam(name = "assetId", value = "Asset ID", required = true, dataType = "string", paramType = "path")
    ))
  def balance: Route =
    (get & path("balance" / Segment / Segment)) { (address, assetId) =>
      complete(balanceJson(address, assetId))
    }

  def assetDistributionTask(assetId: AssetId, maybeHeight: Option[Int]): Task[ToResponseMarshallable] = {
    val currHeightDistributionTask: Task[Either[ValidationError, Map[Address, Long]]] =
      Task
        .eval(blockchain.assetDistribution(assetId))
        .map(_.asRight[ValidationError])

    val distributionTask = maybeHeight
      .fold(currHeightDistributionTask) { height =>
        Task.eval(
          blockchain
            .assetDistributionAtHeight(assetId, height)
        )
      }

    distributionTask.map {
      case Right(dst) => Json.toJson(dst.map { case (a, b) => a.stringRepr -> b }): ToResponseMarshallable
      case Left(err)  => ApiError.fromValidationError(err)
    }
  }

  @Path("/{assetId}/distribution")
  @ApiOperation(value = "token balance distribution", notes = "token balance distribution by account", httpMethod = "GET")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "assetId", value = "Asset ID", required = true, dataType = "string", paramType = "path")
    ))
  def balanceDistribution: Route =
    (get & path(Segment / "distribution")) { assetId =>
      val distributionTask: Task[ToResponseMarshallable] =
        Success(assetId).filter(_.length <= AssetIdStringLength).flatMap(Base58.decode) match {
          case Success(byteArray) =>
            assetDistributionTask(ByteStr(byteArray), None)
          case Failure(_) =>
            Task.pure(ApiError.fromValidationError(com.wavesplatform.transaction.ValidationError.GenericError("Must be base58-encoded assetId")))
        }

      complete {
        try {
          distributionTask.runAsyncLogErr(distributionTaskScheduler)
        } catch {
          case _: RejectedExecutionException =>
            val errMsg = CustomValidationError("Asset distribution currently unavailable, try again later")
            Future.successful(errMsg.json: ToResponseMarshallable)
        }
      }
    }

  @Path("/{assetId}/distribution/{height}")
  @ApiOperation(
    value = "Asset balance distribution at height",
    notes = "Asset balance distribution by account at specified height",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "assetId", value = "Asset ID", required = true, dataType = "string", paramType = "path"),
      new ApiImplicitParam(name = "height", value = "Height", required = true, dataType = "integer", paramType = "path"),
    ))
  def balanceDistributionAtHeight: Route =
    (get & path(Segment / "distribution" / IntNumber)) { (assetId, height) =>
      val distributionTask: Task[ToResponseMarshallable] =
        Success(assetId).filter(_.length <= AssetIdStringLength).flatMap(Base58.decode) match {
          case Success(byteArray) =>
            assetDistributionTask(ByteStr(byteArray), Some(height))

          case Failure(_) =>
            Task.pure(ApiError.fromValidationError(com.wavesplatform.transaction.ValidationError.GenericError("Must be base58-encoded assetId")))
        }

      complete {
        try {
          distributionTask.runAsyncLogErr(distributionTaskScheduler)
        } catch {
          case _: RejectedExecutionException =>
            val errMsg = CustomValidationError("Asset distribution currently unavailable, try again later")
            Future.successful(errMsg.json: ToResponseMarshallable)
        }
      }
    }

  @Path("/balance/{address}")
  @ApiOperation(value = "Account's balance", notes = "Account's balances for all tokens", httpMethod = "GET")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "address", value = "Address", required = true, dataType = "string", paramType = "path")
    ))
  def balances: Route =
    (get & path("balance" / Segment)) { address =>
      complete(fullAccountAssetsInfo(address))
    }

  @Path("/details/{assetId}")
  @ApiOperation(value = "Information about an token", notes = "Provides detailed information about given token", httpMethod = "GET")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "assetId", value = "ID of the asset", required = true, dataType = "string", paramType = "path"),
      new ApiImplicitParam(name = "full", value = "false", required = false, dataType = "boolean", paramType = "query")
    ))
  def details: Route =
    (get & path("details" / Segment)) { id =>
      parameters('full.as[Boolean].?) { full =>
        complete(assetDetails(id, full.getOrElse(false)))
      }
    }

<<<<<<< HEAD
  @Path("/transfer")
  @ApiOperation(value = "Transfer token",
                notes = "Transfer token to new address",
                httpMethod = "POST",
                produces = "application/json",
                consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "com.wavesplatform.api.http.assets.TransferV2Request"
      )
    ))
=======
>>>>>>> 3ed6509985baf3fbb9364f1e34f19a473ab95339
  def transfer: Route =
    processRequest[TransferRequests](
      "transfer", { req =>
        req.eliminate(
          x => doBroadcast(TransactionFactory.transferAssetV1(x, wallet, time)),
          _.eliminate(
            x => doBroadcast(TransactionFactory.transferAssetV2(x, wallet, time)),
            _ => Future.successful(WrongJson(Some(new IllegalArgumentException("Doesn't know how to process request"))))
          )
        )
      }
    )

<<<<<<< HEAD
  @Path("/masstransfer")
  @ApiOperation(value = "Mass Transfer",
                notes = "Mass transfer of tokens",
                httpMethod = "POST",
                produces = "application/json",
                consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "com.wavesplatform.api.http.assets.MassTransferRequest"
      )
    ))
  def massTransfer: Route =
    processRequest("masstransfer", (t: MassTransferRequest) => doBroadcast(TransactionFactory.massTransferAsset(t, wallet, time)))

  @Path("/issue")
  @ApiOperation(value = "Issue token", notes = "Issue new token", httpMethod = "POST", produces = "application/json", consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "com.wavesplatform.api.http.assets.IssueV1Request",
        defaultValue =
          "{\"sender\":\"string\",\"name\":\"str\",\"description\":\"string\",\"quantity\":100000,\"decimals\":7,\"reissuable\":false,\"fee\":100000000}"
      )
    ))
  def issue: Route =
    processRequest("issue", (r: IssueV1Request) => doBroadcast(TransactionFactory.issueAssetV1(r, wallet, time)))

  @Path("/reissue")
  @ApiOperation(value = "Issue token", notes = "Reissue token", httpMethod = "POST", produces = "application/json", consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "com.wavesplatform.api.http.assets.ReissueV1Request",
        example = "{\"sender\":\"string\",\"assetId\":\"Base58\",\"quantity\":100000,\"reissuable\":false,\"fee\":1}"
      )
    ))
  def reissue: Route =
    processRequest("reissue", (r: ReissueV1Request) => doBroadcast(TransactionFactory.reissueAssetV1(r, wallet, time)))

  @Path("/burn")
  @ApiOperation(value = "Burn token",
                notes = "Burn some of your tokens",
                httpMethod = "POST",
                produces = "application/json",
                consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "com.wavesplatform.api.http.assets.BurnV1Request",
        example = "{\"sender\":\"string\",\"assetId\":\"Base58\",\"quantity\":100,\"fee\":100000}"
      )
    ))
=======
  def massTransfer: Route =
    processRequest("masstransfer", (t: MassTransferRequest) => doBroadcast(TransactionFactory.massTransferAsset(t, wallet, time)))

  def issue: Route =
    processRequest("issue", (r: IssueV1Request) => doBroadcast(TransactionFactory.issueAssetV1(r, wallet, time)))

  def reissue: Route =
    processRequest("reissue", (r: ReissueV1Request) => doBroadcast(TransactionFactory.reissueAssetV1(r, wallet, time)))

>>>>>>> 3ed6509985baf3fbb9364f1e34f19a473ab95339
  def burnRoute: Route =
    processRequest("burn", (b: BurnV1Request) => doBroadcast(TransactionFactory.burnAssetV1(b, wallet, time)))

  def signOrder: Route =
    processRequest("order", (order: Order) => {
      wallet.privateKeyAccount(order.senderPublicKey).map(pk => Order.sign(order, pk))
    })

  private def balanceJson(address: String, assetIdStr: String): Either[ApiError, JsObject] = {
    ByteStr.decodeBase58(assetIdStr) match {
      case Success(assetId) =>
        (for {
          acc <- Address.fromString(address)
        } yield
          Json.obj("address" -> acc.address,
                   "assetId" -> assetIdStr,
                   "balance" -> JsNumber(BigDecimal(blockchain.portfolio(acc).assets.getOrElse(assetId, 0L))))).left.map(ApiError.fromValidationError)
      case _ => Left(InvalidAddress)
    }
  }

  private def fullAccountAssetsInfo(address: String): Either[ApiError, JsObject] =
    (for {
      acc <- Address.fromString(address)
    } yield {
      Json.obj(
        "address" -> acc.address,
        "balances" -> JsArray(
          (for {
            (assetId, balance) <- blockchain.portfolio(acc).assets
            if balance > 0
            assetInfo                                 <- blockchain.assetDescription(assetId)
            (_, (issueTransaction: IssueTransaction)) <- blockchain.transactionInfo(assetId)
            sponsorBalance = if (assetInfo.sponsorship != 0) {
              Some(blockchain.portfolio(issueTransaction.sender).spendableBalance)
            } else {
              None
            }
          } yield
            Json.obj(
              "assetId"    -> assetId.base58,
              "balance"    -> balance,
              "reissuable" -> assetInfo.reissuable,
              "minSponsoredAssetFee" -> (assetInfo.sponsorship match {
                case 0           => JsNull
                case sponsorship => JsNumber(sponsorship)
              }),
              "sponsorBalance"   -> sponsorBalance,
              "quantity"         -> JsNumber(BigDecimal(assetInfo.totalVolume)),
              "issueTransaction" -> issueTransaction.json()
            )).toSeq)
      )
    }).left.map(ApiError.fromValidationError)

  private def assetDetails(assetId: String, full: Boolean): Either[ApiError, JsObject] =
    (for {
      id <- ByteStr.decodeBase58(assetId).toOption.toRight("Incorrect asset ID")
      tt <- blockchain.transactionInfo(id).toRight("Failed to find issue transaction by ID")
      (h, mtx) = tt
      tx <- (mtx match {
        case t: IssueTransaction => Some(t)
        case _                   => None
      }).toRight("No issue transaction found with given asset ID")
<<<<<<< HEAD
      description <- blockchain.assetDescription(id).toRight("Failed to get description of the token")
      complexity  <- description.script.fold[Either[String, Long]](Right(0))(ScriptCompiler.estimate)
=======
      description <- blockchain.assetDescription(id).toRight("Failed to get description of the asset")
<<<<<<< HEAD
      complexity  <- description.script.fold[Either[String, Long]](Right(0))(script => ScriptCompiler.estimate(script, script.version))
>>>>>>> 1a6b3243dad151498c2106ff6f09c27303a5800f
=======
      script = description.script.filter(_ => full)
      complexity <- script.fold[Either[String, Long]](Right(0))(script => ScriptCompiler.estimate(script, script.version))
>>>>>>> 3ed6509985baf3fbb9364f1e34f19a473ab95339
    } yield {
      JsObject(
        Seq(
          "assetId"        -> JsString(id.base58),
          "issueHeight"    -> JsNumber(h),
          "issueTimestamp" -> JsNumber(tx.timestamp),
          "issuer"         -> JsString(tx.sender.toString),
          "name"           -> JsString(new String(tx.name, Charsets.UTF_8)),
          "description"    -> JsString(new String(tx.description, Charsets.UTF_8)),
          "decimals"       -> JsNumber(tx.decimals.toInt),
          "reissuable"     -> JsBoolean(description.reissuable),
          "quantity"       -> JsNumber(BigDecimal(description.totalVolume)),
          "scripted"       -> JsBoolean(description.script.nonEmpty),
          "minSponsoredAssetFee" -> (description.sponsorship match {
            case 0           => JsNull
            case sponsorship => JsNumber(sponsorship)
          })
        ) ++ (script.toSeq.map { script =>
          "scriptDetails" -> Json.obj(
            "scriptComplexity" -> JsNumber(BigDecimal(complexity)),
            "script"           -> JsString(script.bytes().base64),
            "scriptText"       -> JsString(script.text)
          )
        })
      )
    }).left.map(m => CustomValidationError(m))

<<<<<<< HEAD
  @Path("/sponsor")
  @ApiOperation(value = "Sponsor an token", httpMethod = "POST", produces = "application/json", consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "com.wavesplatform.api.http.assets.SponsorFeeRequest",
        defaultValue = "{\"sender\":\"string\",\"assetId\":\"Base58\",\"minSponsoredAssetFee\":100000000,\"fee\":100000000}"
      )
    ))
=======
>>>>>>> 3ed6509985baf3fbb9364f1e34f19a473ab95339
  def sponsorRoute: Route =
    processRequest("sponsor", (req: SponsorFeeRequest) => doBroadcast(TransactionFactory.sponsor(req, wallet, time)))
}

object AssetsApiRoute {
  val MAX_DISTRIBUTION_TASKS = 5
}
