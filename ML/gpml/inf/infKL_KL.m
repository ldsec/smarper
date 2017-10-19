function [post nlZ dnlZ] = infKL_KL(hyp, mean, cov, lik, x, y)

if hyp.using_lbfgs~=1
	if hyp.is_cached==1 
		global cache_post;
		global cache_nlz;
		global cache_idx;

		post=cache_post(cache_idx);
		nlZ=cache_nlz(cache_idx);
		if nargout>2
			warning('to be implemented\n');
			dnlZ = NaN;
		end
		return 
	end
end

snu2=hyp.snu2;
% GP prior
n = size(x,1);
K = feval(cov{:}, hyp.cov, x);                  % evaluate the covariance matrix
m = feval(mean{:}, hyp.mean, x);                      % evaluate the mean vector
K=snu2*eye(n)+K;

lik_name = func2str(lik{1});

R = chol(K);   
const_term=-0.5*n+sum(log(diag(R)));

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
m_u=hyp.init_m;
C_u = chol(hyp.init_V,'lower');
C_u = C_u-diag(diag(C_u))+diag(log(diag(C_u)));
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

if hyp.using_lbfgs==1
	alla=[m_u; reshape(C_u,n*n,1)];
	global g_pass;
	[alla nlZ g_pass] = lbfgs(alla, y,lik,n,m, hyp, const_term, lik_name,K);
	m_u=alla(1:n,1);
	C_u=reshape(alla(n+1:end,1),n,n);
else
	error('do not support')
end
C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));

alpha=K\(m_u-m);
post_m=m_u;
post_v=sum(C'.*C',1)';

switch lik_name
case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
	[ll_iter, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
otherwise	 
	[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
end

W=-2.0*dv;
sW=sqrt(abs(W)).*sign(W);
L = chol(eye(n)+sW*sW'.*K); %L = chol(sW*K*sW + eye(n)); 
post.sW = sW;                                             % return argument
post.alpha = alpha;
post.L = L;                                              % L'*L=B=eye(n)+sW*K*sW

nlZ=batch_nlz_fullv2(lik, hyp, sW, K, m, alpha, post_m, y);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end

function [nlZ,dnlZ] = margLik(alla,y,lik,n,m, hyp, const_term, lik_name,K)
	global iter_counter;
	iter_counter=iter_counter+1;

	m_u=alla(1:n,1);
	C_u=reshape(alla(n+1:end,1),n,n);

	alpha=K\(m_u-m);
	post_m=m_u;
	C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));
	post_v=sum(C'.*C',1)';
	%post_v(post_v<1e-30)=1e-30;

	if hyp.stochastic_approx==1
		[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
	else
		switch lik_name
		case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
			[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
		otherwise	 
			[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
		end
	end
	nlZ=-sum(ll)+0.5*( trace(K\(C*C')) +alpha'*(K*alpha))-sum(log(diag(C)))+const_term;
	W=-2.0*dv;
	sW=sqrt(abs(W)).*sign(W);
	nlZ2=batch_nlz_fullv2(lik, hyp, sW, K, m, alpha, m_u, y);

	if hyp.save_iter==1
		global cache_nlz_iter;
		global cache_iter;
		cache_iter=[cache_iter;iter_counter];
		cache_nlz_iter=[cache_nlz_iter;nlZ2];
	end

	g_m_u=alpha-df;

	g_C_u=tril(K\C - diag(2.0*dv)*C);
	g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) ) + diag(-1*ones(n,1)) ;

	dnlZ=[g_m_u; reshape(g_C_u,n*n,1)];

	if ~isLegal(dnlZ)
		nlZ=inf;
		dnlZ(isnan(dnlZ))=0;
		dnlZ(isinf(dnlZ))=0;
	end

function [alla nlZ pass] = lbfgs(alla, y,lik,n,m, hyp,const_term, lik_name,K)
	optMinFunc = struct('Display', 'FULL',...
    'Method', 'lbfgs',...
    'DerivativeCheck', 'off',...
    'LS_type', 1,...
    'MaxIter', hyp.max_pass,...
	'LS_interp', 1,...
    'MaxFunEvals', hyp.max_pass,...
    'Corr' , 100,...
    'optTol', 1e-15,...
    'progTol', 1e-15);
	[alla, nlZ, dummy, output] = minFunc(@margLik, alla, optMinFunc, y,lik,n,m, hyp, const_term, lik_name, K);
	pass=output.funcCount;
	%if hyp.save_iter==1
		%disp('here')
		%global cache_nlz_idx;
		%global cache_iter_idx;
		%cache_iter_idx=[cache_iter_idx;output.trace.funcCount+1];
		%cache_nlz_idx=[cache_nlz_idx;output.trace.funcCount+1];
	%end
